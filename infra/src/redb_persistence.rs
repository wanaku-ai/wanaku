//! Embedded redb persistence for the registry.
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

use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};

use redb::{
    Database, ReadTransaction, ReadableDatabase, ReadableTable, TableDefinition, WriteTransaction,
};
use serde::Serialize;
use serde::de::DeserializeOwned;

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
        if let Some(parent) = path.parent() {
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

    fn load_tables(&self) -> Result<RegistrySnapshot, PersistenceError> {
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
        assert_eq!(
            loaded.tools.len(),
            2,
            "same name in two namespaces is two rows"
        );
        assert_eq!(loaded.namespaces.len(), 1);
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
        use crate::registry::InMemoryRegistry;
        use std::time::Duration;
        use wanaku_types::registry::ToolRegistry;

        // The registry persistence worker is detached and may still hold its
        // database handle after the registry is dropped, so the second
        // registry shares the store instead of reopening the locked file.
        let dir = tempfile::tempdir().unwrap();
        let store = RedbStore::open(dir.path().join(DATABASE_FILE)).unwrap();
        {
            let backend = Arc::new(RedbRegistryPersistence::new(store.clone()));
            let registry = InMemoryRegistry::with_persistence(backend);
            registry.load_persisted();
            registry.register_tool(tool(None, "a", "v1"));
            registry.register_tool(tool(None, "b", "v1"));
            assert!(registry.remove_tool(DEFAULT_NAMESPACE, "b"));
            registry.flush_persistence(Duration::from_secs(5)).unwrap();
            assert!(registry.persistence_status().last_error.is_none());
        }
        let backend = Arc::new(RedbRegistryPersistence::new(store));
        let registry = InMemoryRegistry::with_persistence(backend);
        registry.load_persisted();
        assert!(registry.get_tool(DEFAULT_NAMESPACE, "a").is_some());
        assert!(registry.get_tool(DEFAULT_NAMESPACE, "b").is_none());
    }

    #[test]
    fn failed_commit_is_reported_in_registry_status() {
        use crate::registry::InMemoryRegistry;
        use std::time::Duration;
        use wanaku_types::registry::ToolRegistry;

        let dir = tempfile::tempdir().unwrap();
        let store = RedbStore::open(dir.path().join(DATABASE_FILE)).unwrap();
        {
            let txn = store.begin_write().unwrap();
            {
                let mut table = txn.open_table(TOOLS).unwrap();
                table.insert("default/broken", b"{".as_slice()).unwrap();
            }
            txn.commit().unwrap();
        }
        let registry =
            InMemoryRegistry::with_persistence(Arc::new(RedbRegistryPersistence::new(store)));
        registry.load_persisted();
        registry.register_tool(tool(None, "a", "v1"));
        assert!(registry.flush_persistence(Duration::from_secs(5)).is_err());
        assert!(registry.persistence_status().last_error.is_some());
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
}
