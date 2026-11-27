//! Embedded redb persistence for the registry and the audit trail.
//!
//! The database is one file, `wanaku.redb`, in the persistence directory. It
//! needs no external service. Each registry record kind has its own table. A
//! table maps the record key to the JSON form of the record, so record schema
//! changes follow the same serde defaults as the JSON file backend.
//!
//! [`RedbRegistryPersistence::save`] runs one write transaction per snapshot.
//! The transaction writes only the records that changed and removes the
//! records that are gone. It commits with immediate durability, so a returned
//! `Ok` means the change is on disk. A crash before the commit leaves the
//! previous committed state.

use std::collections::{HashMap, HashSet};
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};

use redb::{
    Database, ReadTransaction, ReadableDatabase, ReadableTable, TableDefinition, WriteTransaction,
};
use serde::Serialize;
use serde::de::DeserializeOwned;

use wanaku_types::audit::{AuditEvent, AuditPersistence};
use wanaku_types::config::PersistBackend;
use wanaku_types::persistence::{PersistenceBackend, PersistenceError, RegistrySnapshot};
use wanaku_types::registry::DEFAULT_NAMESPACE;

/// File name of the embedded database in the persistence directory.
pub const DATABASE_FILE: &str = "wanaku.redb";

/// Schema version of the tables in this file. Increase it when a change
/// needs a migration of the stored records.
pub const SCHEMA_VERSION: u64 = 1;

const META: TableDefinition<&str, u64> = TableDefinition::new("meta");
const SCHEMA_VERSION_KEY: &str = "schema_version";

type RecordTable = TableDefinition<'static, &'static str, &'static [u8]>;

const AGENTS: RecordTable = TableDefinition::new("registry.agents");
const TOOLS: RecordTable = TableDefinition::new("registry.tools");
const RESOURCES: RecordTable = TableDefinition::new("registry.resources");
const PROMPTS: RecordTable = TableDefinition::new("registry.prompts");
const FORWARDS: RecordTable = TableDefinition::new("registry.forwards");
const NAMESPACES: RecordTable = TableDefinition::new("registry.namespaces");
const BINDINGS: RecordTable = TableDefinition::new("registry.bindings");

const REGISTRY_TABLES: [RecordTable; 7] = [
    AGENTS, TOOLS, RESOURCES, PROMPTS, FORWARDS, NAMESPACES, BINDINGS,
];

/// Audit events keyed by their sequence number.
const AUDIT_EVENTS: TableDefinition<u64, &[u8]> = TableDefinition::new("audit.events");

fn db_error(error: impl std::fmt::Display) -> PersistenceError {
    PersistenceError::Database(error.to_string())
}

/// An open embedded database. Clones share the same file handle.
///
/// redb holds a file lock while the database is open. A second process that
/// opens the same file fails with an error, so two servers cannot write to
/// one database.
#[derive(Clone)]
pub struct RedbStore {
    db: Arc<Database>,
    path: PathBuf,
}

impl RedbStore {
    /// Open or create the database at `path` and check its schema version.
    ///
    /// redb repairs the file after an unclean shutdown when it opens it.
    /// A corrupt file, a file from a newer schema version, or a file that
    /// another process holds open returns an error.
    pub fn open(path: impl Into<PathBuf>) -> Result<Self, PersistenceError> {
        let path = path.into();
        // A bare file name has an empty parent: use the current directory.
        if let Some(parent) = path.parent().filter(|p| !p.as_os_str().is_empty()) {
            std::fs::create_dir_all(parent)?;
        }
        let db = Database::create(&path).map_err(db_error)?;
        let store = Self {
            db: Arc::new(db),
            path,
        };
        store.initialize()?;
        Ok(store)
    }

    /// Open the database in the configured persistence directory when
    /// `WANAKU_PERSIST_BACKEND=redb`. Returns `Ok(None)` for other backends.
    pub fn from_config() -> Result<Option<Self>, PersistenceError> {
        match wanaku_types::config::ENV.persist.as_ref() {
            Some(persist) if persist.backend == PersistBackend::Redb => {
                Self::open(persist.dir.join(DATABASE_FILE)).map(Some)
            }
            _ => Ok(None),
        }
    }

    #[must_use]
    pub fn path(&self) -> &Path {
        &self.path
    }

    fn initialize(&self) -> Result<(), PersistenceError> {
        let txn = self.db.begin_write().map_err(db_error)?;
        {
            let mut meta = txn.open_table(META).map_err(db_error)?;
            let stored = meta
                .get(SCHEMA_VERSION_KEY)
                .map_err(db_error)?
                .map(|value| value.value());
            match stored {
                None => {
                    meta.insert(SCHEMA_VERSION_KEY, SCHEMA_VERSION)
                        .map_err(db_error)?;
                }
                Some(SCHEMA_VERSION) => {}
                Some(other) => {
                    return Err(PersistenceError::Database(format!(
                        "unsupported schema version {other} in {}; this server supports version {SCHEMA_VERSION}",
                        self.path.display()
                    )));
                }
            }
            for table in REGISTRY_TABLES {
                txn.open_table(table).map_err(db_error)?;
            }
            txn.open_table(AUDIT_EVENTS).map_err(db_error)?;
        }
        txn.commit().map_err(db_error)
    }

    fn begin_read(&self) -> Result<ReadTransaction, PersistenceError> {
        self.db.begin_read().map_err(db_error)
    }

    fn begin_write(&self) -> Result<WriteTransaction, PersistenceError> {
        self.db.begin_write().map_err(db_error)
    }
}

/// Registry persistence in redb tables, one record per row.
pub struct RedbRegistryPersistence {
    store: RedbStore,
    /// Set when a load fails. Saves are then refused, so that a registry
    /// that started empty cannot delete the stored records.
    load_failed: AtomicBool,
}

impl RedbRegistryPersistence {
    #[must_use]
    pub const fn new(store: RedbStore) -> Self {
        Self {
            store,
            load_failed: AtomicBool::new(false),
        }
    }

    /// Read every table and check the records the registry would otherwise
    /// skip at load time. A record that the registry skips would be deleted
    /// by the next save, so such a record fails the whole load.
    fn load_tables(&self) -> Result<RegistrySnapshot, PersistenceError> {
        let snapshot = self.read_tables()?;
        for agent in &snapshot.agents {
            crate::agents::RegisteredAgent::new(agent.clone()).map_err(|error| {
                PersistenceError::Database(format!(
                    "invalid agent {}/{}: {error}",
                    agent.namespace, agent.name
                ))
            })?;
        }
        crate::registry::RuntimeRegistry::new()
            .validate_snapshot_namespaces(&snapshot)
            .map_err(|error| {
                PersistenceError::Database(format!("invalid namespace graph: {error}"))
            })?;
        Ok(snapshot)
    }

    fn read_tables(&self) -> Result<RegistrySnapshot, PersistenceError> {
        let txn = self.store.begin_read()?;
        Ok(RegistrySnapshot {
            agents: read_records(&txn, AGENTS)?,
            tools: read_records(&txn, TOOLS)?,
            resources: read_records(&txn, RESOURCES)?,
            prompts: read_records(&txn, PROMPTS)?,
            forwards: read_records(&txn, FORWARDS)?,
            namespaces: read_records(&txn, NAMESPACES)?,
            bindings: read_records(&txn, BINDINGS)?,
        })
    }
}

impl PersistenceBackend for RedbRegistryPersistence {
    fn load(&self) -> Result<RegistrySnapshot, PersistenceError> {
        let result = self.load_tables();
        self.load_failed.store(result.is_err(), Ordering::Release);
        result
    }

    fn save(&self, snapshot: &RegistrySnapshot) -> Result<(), PersistenceError> {
        if self.load_failed.load(Ordering::Acquire) {
            return Err(PersistenceError::Database(format!(
                "registry in {} was not loaded; refusing to overwrite stored records",
                self.store.path().display()
            )));
        }
        let txn = self.store.begin_write()?;
        sync_records(&txn, AGENTS, &snapshot.agents, |agent| {
            format!("{}/{}", agent.namespace, agent.name)
        })?;
        sync_records(&txn, TOOLS, &snapshot.tools, |entry| {
            scoped_key(entry.namespace.as_deref(), &entry.name)
        })?;
        sync_records(&txn, RESOURCES, &snapshot.resources, |entry| {
            scoped_key(entry.namespace.as_deref(), &entry.name)
        })?;
        sync_records(&txn, PROMPTS, &snapshot.prompts, |entry| {
            scoped_key(entry.namespace.as_deref(), &entry.name)
        })?;
        sync_records(&txn, FORWARDS, &snapshot.forwards, |entry| {
            entry.name.clone()
        })?;
        sync_records(&txn, NAMESPACES, &snapshot.namespaces, |entry| {
            entry.name.clone()
        })?;
        sync_records(&txn, BINDINGS, &snapshot.bindings, |entry| entry.id.clone())?;
        txn.commit().map_err(db_error)
    }
}

/// Audit event persistence in one redb table, one row per event.
///
/// The audit store hands over its whole retained window on each save. Events
/// do not change after they are recorded, so a save inserts only the events
/// with a sequence above the last stored row and deletes the rows that left
/// the window. Retention is the audit store capacity. A retried save writes
/// each event to the same key, so a retry cannot duplicate events.
pub struct RedbAuditPersistence {
    store: RedbStore,
    load_failed: AtomicBool,
}

impl RedbAuditPersistence {
    #[must_use]
    pub const fn new(store: RedbStore) -> Self {
        Self {
            store,
            load_failed: AtomicBool::new(false),
        }
    }

    fn read_events(&self) -> Result<Vec<AuditEvent>, PersistenceError> {
        let txn = self.store.begin_read()?;
        let table = txn.open_table(AUDIT_EVENTS).map_err(db_error)?;
        let mut events = Vec::new();
        for row in table.iter().map_err(db_error)? {
            let (sequence, value) = row.map_err(db_error)?;
            let event = serde_json::from_slice(value.value()).map_err(|error| {
                PersistenceError::Database(format!(
                    "invalid audit event {}: {error}",
                    sequence.value()
                ))
            })?;
            events.push(event);
        }
        Ok(events)
    }

    fn write_events(&self, events: &[AuditEvent]) -> Result<(), PersistenceError> {
        let txn = self.store.begin_write()?;
        {
            let mut table = txn.open_table(AUDIT_EVENTS).map_err(db_error)?;
            drop_events_below(&mut table, events.iter().map(|event| event.sequence).min())?;
            let last_stored = table.last().map_err(db_error)?.map(|(key, _)| key.value());
            for event in events
                .iter()
                .filter(|event| last_stored.is_none_or(|last| event.sequence > last))
            {
                let value = serde_json::to_vec(event)?;
                table
                    .insert(event.sequence, value.as_slice())
                    .map_err(db_error)?;
            }
            // The store can also evict an external event from the middle of
            // the window. Remove such rows only when the counts disagree.
            if redb::ReadableTableMetadata::len(&table).map_err(db_error)? > events.len() as u64 {
                let retained: HashSet<u64> = events.iter().map(|event| event.sequence).collect();
                table
                    .retain(|sequence, _| retained.contains(&sequence))
                    .map_err(db_error)?;
            }
        }
        txn.commit().map_err(db_error)
    }
}

impl AuditPersistence for RedbAuditPersistence {
    fn load(&self) -> Result<Vec<AuditEvent>, String> {
        let result = self.read_events();
        self.load_failed.store(result.is_err(), Ordering::Release);
        result.map_err(|error| error.to_string())
    }

    fn save(&self, events: &[AuditEvent]) -> Result<(), String> {
        if self.load_failed.load(Ordering::Acquire) {
            return Err(format!(
                "audit events in {} were not loaded; refusing to overwrite stored events",
                self.store.path().display()
            ));
        }
        self.write_events(events).map_err(|error| error.to_string())
    }
}

/// Oldest events leave the audit window first: remove the rows below the
/// first retained sequence, or every row when nothing is retained.
fn drop_events_below(
    table: &mut redb::Table<'_, u64, &[u8]>,
    first_retained: Option<u64>,
) -> Result<(), PersistenceError> {
    loop {
        let first = table.first().map_err(db_error)?.map(|(key, _)| key.value());
        match first {
            Some(sequence) if first_retained.is_none_or(|kept| sequence < kept) => {
                table.remove(sequence).map_err(db_error)?;
            }
            _ => return Ok(()),
        }
    }
}

/// Namespace names are DNS labels and contain no `/`, so the first `/`
/// separates the namespace from the name.
fn scoped_key(namespace: Option<&str>, name: &str) -> String {
    format!("{}/{name}", namespace.unwrap_or(DEFAULT_NAMESPACE))
}

fn read_records<T: DeserializeOwned>(
    txn: &ReadTransaction,
    definition: RecordTable,
) -> Result<Vec<T>, PersistenceError> {
    let table = txn.open_table(definition).map_err(db_error)?;
    let mut records = Vec::new();
    for row in table.iter().map_err(db_error)? {
        let (key, value) = row.map_err(db_error)?;
        let record = serde_json::from_slice(value.value()).map_err(|error| {
            PersistenceError::Database(format!(
                "invalid record {} in table {definition}: {error}",
                key.value()
            ))
        })?;
        records.push(record);
    }
    Ok(records)
}

/// Make the table hold exactly `records`. Rows with an unchanged value are
/// not written again.
fn sync_records<T: Serialize>(
    txn: &WriteTransaction,
    definition: RecordTable,
    records: &[T],
    key_of: impl Fn(&T) -> String,
) -> Result<(), PersistenceError> {
    let mut wanted = HashMap::with_capacity(records.len());
    for record in records {
        wanted.insert(key_of(record), serde_json::to_vec(record)?);
    }
    let mut table = txn.open_table(definition).map_err(db_error)?;
    table
        .retain(|key, _| wanted.contains_key(key))
        .map_err(db_error)?;
    for (key, value) in &wanted {
        let unchanged = table
            .get(key.as_str())
            .map_err(db_error)?
            .is_some_and(|stored| stored.value() == value.as_slice());
        if !unchanged {
            table
                .insert(key.as_str(), value.as_slice())
                .map_err(db_error)?;
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use std::collections::HashMap;

    use super::*;
    use wanaku_types::registry::{NamespaceEntry, ToolEntry};

    fn tool(namespace: Option<&str>, name: &str, description: &str) -> ToolEntry {
        ToolEntry {
            name: name.to_owned(),
            description: description.to_owned(),
            uri: "test://uri".to_owned(),
            type_: "mcp".to_owned(),
            mcp: Default::default(),
            input_schema: serde_json::json!({"type": "object"}),
            labels: HashMap::new(),
            id: None,
            namespace: namespace.map(str::to_owned),
            forward_id: None,
            enabled: true,
        }
    }

    fn namespace(name: &str) -> NamespaceEntry {
        serde_json::from_value(serde_json::json!({ "name": name })).unwrap()
    }

    fn row_count(store: &RedbStore, definition: RecordTable) -> u64 {
        let txn = store.begin_read().unwrap();
        let table = txn.open_table(definition).unwrap();
        redb::ReadableTableMetadata::len(&table).unwrap()
    }

    #[test]
    fn empty_database_loads_empty_snapshot() {
        let dir = tempfile::tempdir().unwrap();
        let store = RedbStore::open(dir.path().join(DATABASE_FILE)).unwrap();
        let snapshot = RedbRegistryPersistence::new(store).load().unwrap();
        assert!(snapshot.tools.is_empty());
        assert!(snapshot.namespaces.is_empty());
    }

    #[test]
    fn records_survive_reopen() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join(DATABASE_FILE);
        {
            let backend = RedbRegistryPersistence::new(RedbStore::open(&path).unwrap());
            let snapshot = RegistrySnapshot {
                tools: vec![
                    tool(None, "a", "first"),
                    tool(Some("finance"), "a", "second"),
                ],
                namespaces: vec![namespace("finance")],
                ..RegistrySnapshot::default()
            };
            backend.save(&snapshot).unwrap();
        }
        let backend = RedbRegistryPersistence::new(RedbStore::open(&path).unwrap());
        let loaded = backend.load().unwrap();
        let mut tools: Vec<_> = loaded
            .tools
            .iter()
            .map(|t| (&t.namespace, &t.description))
            .collect();
        tools.sort();
        let (finance, first, second) = (
            Some("finance".to_owned()),
            "first".to_owned(),
            "second".to_owned(),
        );
        assert_eq!(tools, vec![(&None, &first), (&finance, &second)]);
        let namespaces: Vec<_> = loaded.namespaces.iter().map(|n| n.name.as_str()).collect();
        assert_eq!(namespaces, vec!["finance"]);
    }

    #[test]
    fn save_updates_and_removes_individual_rows() {
        let dir = tempfile::tempdir().unwrap();
        let store = RedbStore::open(dir.path().join(DATABASE_FILE)).unwrap();
        let backend = RedbRegistryPersistence::new(store.clone());
        backend
            .save(&RegistrySnapshot {
                tools: vec![tool(None, "a", "v1"), tool(None, "b", "v1")],
                ..RegistrySnapshot::default()
            })
            .unwrap();
        backend
            .save(&RegistrySnapshot {
                tools: vec![tool(None, "a", "v2")],
                ..RegistrySnapshot::default()
            })
            .unwrap();

        assert_eq!(row_count(&store, TOOLS), 1);
        let loaded = backend.load().unwrap();
        assert_eq!(loaded.tools.len(), 1);
        assert_eq!(loaded.tools[0].description, "v2");
    }

    #[test]
    fn invalid_record_fails_load_and_blocks_saves() {
        let dir = tempfile::tempdir().unwrap();
        let store = RedbStore::open(dir.path().join(DATABASE_FILE)).unwrap();
        {
            let txn = store.begin_write().unwrap();
            {
                let mut table = txn.open_table(TOOLS).unwrap();
                table
                    .insert("default/broken", b"not json".as_slice())
                    .unwrap();
            }
            txn.commit().unwrap();
        }
        let backend = RedbRegistryPersistence::new(store.clone());
        assert!(backend.load().is_err());
        assert!(
            backend.save(&RegistrySnapshot::default()).is_err(),
            "a failed load must not let an empty registry delete stored rows"
        );
        assert_eq!(row_count(&store, TOOLS), 1);
    }

    #[test]
    fn newer_schema_version_is_rejected() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join(DATABASE_FILE);
        {
            let store = RedbStore::open(&path).unwrap();
            let txn = store.begin_write().unwrap();
            {
                let mut meta = txn.open_table(META).unwrap();
                meta.insert(SCHEMA_VERSION_KEY, SCHEMA_VERSION + 1).unwrap();
            }
            txn.commit().unwrap();
        }
        let error = RedbStore::open(&path).err().map(|e| e.to_string());
        assert!(error.is_some_and(|e| e.contains("unsupported schema version")));
    }

    #[test]
    fn registry_changes_reach_redb_through_the_persistence_worker() {
        use crate::registry::RuntimeRegistry;
        use std::time::Duration;
        use wanaku_types::registry::ToolRegistry;

        // The registry persistence worker is detached and may still hold its
        // database handle after the registry is dropped, so the second
        // registry shares the store instead of reopening the locked file.
        let dir = tempfile::tempdir().unwrap();
        let store = RedbStore::open(dir.path().join(DATABASE_FILE)).unwrap();
        {
            let backend = Arc::new(RedbRegistryPersistence::new(store.clone()));
            let registry = RuntimeRegistry::with_persistence(backend);
            registry.load_persisted();
            registry.register_tool(tool(None, "a", "v1"));
            registry.register_tool(tool(None, "b", "v1"));
            assert!(registry.remove_tool(DEFAULT_NAMESPACE, "b"));
            registry.flush_persistence(Duration::from_secs(5)).unwrap();
            assert!(registry.persistence_status().last_error.is_none());
        }
        let backend = Arc::new(RedbRegistryPersistence::new(store));
        let registry = RuntimeRegistry::with_persistence(backend);
        registry.load_persisted();
        let reloaded = registry.get_tool(DEFAULT_NAMESPACE, "a").unwrap();
        assert_eq!(reloaded.description, "v1");
        assert_eq!(reloaded.uri, "test://uri");
        assert!(registry.get_tool(DEFAULT_NAMESPACE, "b").is_none());
    }

    /// An in-memory storage backend whose writes fail once `fail` is set.
    #[derive(Debug)]
    struct FailingBackend {
        inner: redb::backends::InMemoryBackend,
        fail: Arc<AtomicBool>,
    }

    impl FailingBackend {
        fn check(&self) -> Result<(), std::io::Error> {
            if self.fail.load(Ordering::Acquire) {
                return Err(std::io::Error::other("injected write failure"));
            }
            Ok(())
        }
    }

    impl redb::StorageBackend for FailingBackend {
        fn len(&self) -> Result<u64, std::io::Error> {
            self.inner.len()
        }
        fn read(&self, offset: u64, out: &mut [u8]) -> Result<(), std::io::Error> {
            self.inner.read(offset, out)
        }
        fn set_len(&self, len: u64) -> Result<(), std::io::Error> {
            self.check()?;
            self.inner.set_len(len)
        }
        fn sync_data(&self) -> Result<(), std::io::Error> {
            self.check()?;
            self.inner.sync_data()
        }
        fn write(&self, offset: u64, data: &[u8]) -> Result<(), std::io::Error> {
            self.check()?;
            self.inner.write(offset, data)
        }
    }

    fn failing_store(fail: &Arc<AtomicBool>) -> RedbStore {
        let db = Database::builder()
            .create_with_backend(FailingBackend {
                inner: redb::backends::InMemoryBackend::new(),
                fail: Arc::clone(fail),
            })
            .unwrap();
        let store = RedbStore {
            db: Arc::new(db),
            path: PathBuf::from("in-memory"),
        };
        store.initialize().unwrap();
        store
    }

    #[test]
    fn failed_commit_is_reported_in_registry_status() {
        use crate::registry::RuntimeRegistry;
        use std::time::Duration;
        use wanaku_types::registry::ToolRegistry;

        let fail = Arc::new(AtomicBool::new(false));
        let store = failing_store(&fail);
        let registry = RuntimeRegistry::with_persistence(Arc::new(RedbRegistryPersistence::new(
            store.clone(),
        )));
        registry.load_persisted();
        registry.register_tool(tool(None, "a", "v1"));
        registry.flush_persistence(Duration::from_secs(5)).unwrap();

        fail.store(true, Ordering::Release);
        registry.register_tool(tool(None, "b", "v1"));
        assert!(registry.flush_persistence(Duration::from_secs(5)).is_err());
        assert!(registry.persistence_status().last_error.is_some());

        fail.store(false, Ordering::Release);
        let stored = RedbRegistryPersistence::new(store).load().unwrap();
        let names: Vec<_> = stored.tools.iter().map(|t| t.name.as_str()).collect();
        assert_eq!(
            names,
            vec!["a"],
            "the failed commit left the previous state"
        );
    }

    #[test]
    fn invalid_agent_fails_load_and_blocks_saves() {
        let dir = tempfile::tempdir().unwrap();
        let store = RedbStore::open(dir.path().join(DATABASE_FILE)).unwrap();
        {
            let txn = store.begin_write().unwrap();
            {
                let mut table = txn.open_table(AGENTS).unwrap();
                let agent = br#"{"name":"agent","namespace":"default","address":"not a url"}"#;
                table.insert("default/agent", agent.as_slice()).unwrap();
            }
            txn.commit().unwrap();
        }
        let backend = RedbRegistryPersistence::new(store.clone());
        assert!(backend.load().is_err());
        assert!(backend.save(&RegistrySnapshot::default()).is_err());
        assert_eq!(row_count(&store, AGENTS), 1);
    }

    #[test]
    fn corrupt_file_is_rejected() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join(DATABASE_FILE);
        std::fs::write(&path, b"this is not a redb database").unwrap();
        assert!(RedbStore::open(&path).is_err());
    }

    #[test]
    fn second_open_of_the_same_file_fails() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join(DATABASE_FILE);
        let _first = RedbStore::open(&path).unwrap();
        assert!(RedbStore::open(&path).is_err());
    }

    fn audit_event(sequence: u64) -> AuditEvent {
        use wanaku_types::audit::{AuditCategory, AuditDecision};
        let mut event = AuditEvent::new(
            AuditCategory::Decision,
            AuditDecision::Allow,
            wanaku_types::TOOLS_CALL,
            "allowed",
            "Allowed",
        );
        event.sequence = sequence;
        event
    }

    fn audit_rows(store: &RedbStore) -> Vec<u64> {
        let txn = store.begin_read().unwrap();
        let table = txn.open_table(AUDIT_EVENTS).unwrap();
        table
            .iter()
            .unwrap()
            .map(|row| row.unwrap().0.value())
            .collect()
    }

    #[test]
    fn audit_store_appends_and_applies_retention() {
        use crate::audit::InMemoryAuditStore;
        use wanaku_types::audit::AuditStore;

        let dir = tempfile::tempdir().unwrap();
        let store = RedbStore::open(dir.path().join(DATABASE_FILE)).unwrap();
        let mut recorded = Vec::new();
        {
            let backend = Arc::new(RedbAuditPersistence::new(store.clone()));
            let audit = InMemoryAuditStore::with_persistence(3, backend);
            for _ in 0..5 {
                let event = audit_event(0);
                recorded.push(event.event_id.clone());
                audit.record(event);
            }
            // Dropping the store flushes the pending events.
        }
        assert_eq!(
            audit_rows(&store),
            vec![2, 3, 4],
            "capacity is the retention"
        );
        let loaded = RedbAuditPersistence::new(store).load().unwrap();
        let ids: Vec<_> = loaded.iter().map(|event| event.event_id.clone()).collect();
        assert_eq!(ids, recorded[2..].to_vec());
    }

    #[test]
    fn repeated_audit_save_does_not_duplicate_events() {
        let dir = tempfile::tempdir().unwrap();
        let store = RedbStore::open(dir.path().join(DATABASE_FILE)).unwrap();
        let backend = RedbAuditPersistence::new(store.clone());
        let window = vec![audit_event(1), audit_event(2)];
        backend.save(&window).unwrap();
        backend.save(&window).unwrap();
        assert_eq!(audit_rows(&store), vec![1, 2]);
    }

    #[test]
    fn failed_audit_commit_writes_nothing_and_later_writes_need_a_restart() {
        let fail = Arc::new(AtomicBool::new(false));
        let store = failing_store(&fail);
        let backend = RedbAuditPersistence::new(store.clone());
        backend.save(&[audit_event(1)]).unwrap();
        fail.store(true, Ordering::Release);
        assert!(backend.save(&[audit_event(1), audit_event(2)]).is_err());
        fail.store(false, Ordering::Release);
        assert_eq!(
            audit_rows(&store),
            vec![1],
            "the failed commit wrote nothing"
        );
        // redb refuses writes after a storage I/O error until the database
        // is opened again, which in Wanaku means a restart.
        assert!(backend.save(&[audit_event(1), audit_event(2)]).is_err());
    }

    #[test]
    fn audit_save_removes_events_evicted_from_the_middle() {
        let dir = tempfile::tempdir().unwrap();
        let store = RedbStore::open(dir.path().join(DATABASE_FILE)).unwrap();
        let backend = RedbAuditPersistence::new(store.clone());
        backend
            .save(&[audit_event(1), audit_event(2), audit_event(3)])
            .unwrap();
        backend
            .save(&[audit_event(1), audit_event(3), audit_event(4)])
            .unwrap();
        assert_eq!(audit_rows(&store), vec![1, 3, 4]);
    }

    #[test]
    fn invalid_audit_row_fails_load_and_blocks_saves() {
        let dir = tempfile::tempdir().unwrap();
        let store = RedbStore::open(dir.path().join(DATABASE_FILE)).unwrap();
        {
            let txn = store.begin_write().unwrap();
            {
                let mut table = txn.open_table(AUDIT_EVENTS).unwrap();
                table.insert(7, b"not json".as_slice()).unwrap();
            }
            txn.commit().unwrap();
        }
        let backend = RedbAuditPersistence::new(store.clone());
        assert!(backend.load().is_err());
        assert!(backend.save(&[]).is_err());
        assert_eq!(audit_rows(&store), vec![7]);
    }
}
