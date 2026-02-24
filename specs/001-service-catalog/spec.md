# Feature Specification: Service Catalog

**Feature Branch**: `001-service-catalog`
**Created**: 2026-02-22
**Status**: Draft
**Input**: User description: "Build a service catalog page. The service catalog is a new feature that can be used to create, update, delete and manage service catalogs. A service catalog is a new concept that represents a system that can be accessed by Wanaku (via the Camel Integration Capability), how that system can be accessed and what they expose (i.e.: the rules to access that system). A service can be composed of one or more systems (for instance, consider a scenario where there is a service for 'finance' and that service is composed of both the current finance system, as well as a legacy system that contains historical data - in this case, there could be 2 different camel route files [as in, different yaml files] with multiple routes each, representing the systems they access)."

## Clarifications

### Session 2026-02-23

- Q: What is the relationship between the service catalog and the existing Capabilities/Targets system? → A: The service catalog is a higher-level organizational layer that groups and references existing registered capabilities/targets.
- Q: What does a "System" within a service contain? → A: A system references existing capabilities/targets and also carries its own descriptive metadata (name, description, route file reference).
- Q: Should services be scoped to a single namespace or span multiple? → A: Cross-namespace. A service can reference capabilities from any namespace.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View Service Catalog (Priority: P1)

As an administrator, I want to see a list of all defined services in a catalog view so that I can understand what services are available, what systems compose each service, and how they are configured.

**Why this priority**: Viewing existing services is the foundational capability. Without it, no other management operations (create, edit, delete) provide value. This is the read-only MVP that lets users understand their service landscape.

**Independent Test**: Can be fully tested by navigating to the service catalog page and verifying that all configured services are displayed with their associated systems, route files, and access rules.

**Acceptance Scenarios**:

1. **Given** the service catalog contains one or more services, **When** I navigate to the service catalog page, **Then** I see a list of services with their name, description, and the number of systems each service contains.
2. **Given** a service has multiple systems, **When** I expand or select that service, **Then** I see the individual systems with their route file references and descriptions.
3. **Given** no services exist in the catalog, **When** I navigate to the service catalog page, **Then** I see an empty state with a prompt to create a new service.

---

### User Story 2 - Create a New Service (Priority: P2)

As an administrator, I want to create a new service in the catalog by providing a name, description, and one or more systems (each referencing a Camel route file and its access rules) so that I can register new services for use by Wanaku.

**Why this priority**: Creating services is the primary write operation and is required before editing or deleting makes sense. Combined with the view (P1), this delivers a usable feature.

**Independent Test**: Can be fully tested by creating a new service with at least one system, saving it, and verifying it appears in the catalog list with correct details.

**Acceptance Scenarios**:

1. **Given** I am on the service catalog page, **When** I click the "Add" button, **Then** a form or modal opens allowing me to enter a service name, description, and at least one system definition.
2. **Given** I am filling out the new service form, **When** I add multiple systems to a single service (e.g., a current finance system and a legacy historical system), **Then** each system can specify its own name, description, Camel route file reference, and link to one or more existing registered capabilities/targets.
3. **Given** I have filled out all required fields, **When** I submit the form, **Then** the new service is persisted and appears in the catalog list.
4. **Given** I leave required fields empty, **When** I attempt to submit, **Then** I see validation messages indicating which fields are required.

---

### User Story 3 - Edit an Existing Service (Priority: P3)

As an administrator, I want to edit an existing service to update its name, description, or modify its systems (add, remove, or update system entries) so that the catalog stays current as my infrastructure evolves.

**Why this priority**: Editing is important for ongoing maintenance but is less critical than initial viewing and creation. Services change over time, so this supports day-2 operations.

**Independent Test**: Can be fully tested by selecting an existing service, modifying its details or systems, saving, and verifying the changes are reflected in the catalog.

**Acceptance Scenarios**:

1. **Given** a service exists in the catalog, **When** I select the edit action for that service, **Then** I see a pre-populated form with the current service details and systems.
2. **Given** I am editing a service, **When** I add a new system entry, **Then** the system is appended to the service's list of systems.
3. **Given** I am editing a service, **When** I remove an existing system entry, **Then** the system is removed from the service after confirmation.
4. **Given** I have made changes to a service, **When** I save, **Then** the updated service is persisted and the catalog list reflects the changes.

---

### User Story 4 - Delete a Service (Priority: P4)

As an administrator, I want to delete a service from the catalog so that I can remove services that are no longer needed or were created in error.

**Why this priority**: Deletion is a cleanup operation. While essential for a complete CRUD experience, it is the least frequently used action and carries risk of data loss.

**Independent Test**: Can be fully tested by selecting a service, confirming deletion, and verifying the service no longer appears in the catalog.

**Acceptance Scenarios**:

1. **Given** a service exists in the catalog, **When** I select the delete action, **Then** I am asked to confirm the deletion before it proceeds.
2. **Given** I confirm the deletion, **When** the operation completes, **Then** the service is removed from the catalog and a success notification is shown.
3. **Given** I cancel the deletion confirmation, **When** the dialog closes, **Then** the service remains unchanged in the catalog.

---

### User Story 5 - View System Details Within a Service (Priority: P5)

As an administrator, I want to view detailed information about each system within a service, including its Camel route file reference, the routes it contains, and the access rules it exposes, so that I can understand exactly how a system is configured.

**Why this priority**: Detailed system-level inspection enhances the usefulness of the catalog but is supplementary to the core CRUD operations. It helps with troubleshooting and auditing.

**Independent Test**: Can be fully tested by navigating into a service and inspecting system-level details, verifying route file references and access rule descriptions are displayed accurately.

**Acceptance Scenarios**:

1. **Given** a service with at least one system, **When** I view the system details, **Then** I see the route file reference (YAML file path or identifier), a description of the system, and the access rules it exposes.
2. **Given** a system references a Camel route file with multiple routes, **When** I view its details, **Then** each route within the file is listed with its identifier and description.

---

### Edge Cases

- What happens when a user tries to create a service with a name that already exists? The system should reject the creation and show an error indicating the name must be unique.
- What happens when a user tries to delete a service that is currently in use by an active capability? The system should warn the user and require explicit confirmation.
- What happens when a referenced Camel route file cannot be found or is invalid? The system should display a warning indicator on the affected system but still allow the service to be saved (the file may be deployed later).
- What happens when a service has no systems? The system should allow saving a service with zero systems (e.g., as a placeholder) but display a warning that the service has no configured systems.
- How does the system handle concurrent edits to the same service? The last save wins, consistent with existing patterns in the Wanaku admin UI.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST display a catalog of all services in a tabular or list view, showing service name, description, and system count.
- **FR-002**: System MUST allow users to create a new service by specifying a name, description, and optionally one or more systems.
- **FR-003**: System MUST enforce unique service names within the catalog.
- **FR-004**: System MUST allow users to add multiple systems to a single service, where each system references a Camel route file and includes a description.
- **FR-005**: System MUST allow users to edit an existing service's name, description, and its list of systems.
- **FR-006**: System MUST allow users to delete a service, with a confirmation prompt before removal.
- **FR-007**: System MUST persist service catalog data so that it survives application restarts.
- **FR-008**: System MUST validate required fields (service name is mandatory) and display appropriate error messages when validation fails.
- **FR-009**: System MUST show a loading indicator while service catalog data is being fetched or modified.
- **FR-010**: System MUST display success and error notifications (toasts) for create, update, and delete operations, consistent with existing UI patterns.
- **FR-011**: System MUST allow users to view detailed information about each system within a service, including route file references and access rules.
- **FR-012**: Each system within a service MUST have a name, a description, and a reference to at least one Camel route file (YAML).
- **FR-013**: System MUST provide navigation to the service catalog page from the main application menu.

### Key Entities

- **Service**: Represents a higher-level organizational grouping of one or more systems that together provide a business capability. A service aggregates existing registered capabilities/targets and is not scoped to a single namespace — it can reference capabilities across any namespace. Attributes: unique name, description, list of systems, labels/metadata.
- **System**: Represents an individual backend system accessible via Camel routes. A system belongs to exactly one service, references one or more existing registered capabilities/targets, and carries its own descriptive metadata for organizational context. Attributes: name, description, route file reference (path or identifier to a YAML file), associated capability/target references, access rules. The system does not duplicate capability data; it links to capabilities and adds context about the system's role within the service.
- **Access Rule**: Describes what a system exposes and the constraints for accessing it. Attributes: rule name, description, route identifier within the file, any parameters or constraints.

## Assumptions

- The service catalog is a new concept that sits above the existing "Capabilities/Targets" system. It provides a higher-level organizational view by grouping and referencing existing registered capabilities/targets, rather than replacing them.
- A Camel route file is referenced by path or identifier (e.g., a file name or URI), and the catalog stores this reference rather than the file content itself.
- Access rules are descriptive metadata about what a system exposes, not runtime enforcement rules. They help users understand what each system provides.
- The service catalog follows the same UI patterns (Carbon Design, table layout, modal forms, toast notifications) as the existing Tools and Resources pages.
- Labels/metadata support on services follows the same pattern as existing entities (LabelsAwareEntity).
- Services are cross-namespace: a single service can reference capabilities/targets from any namespace, reflecting its role as a higher-level organizational concept.
- Authentication and authorization follow the existing application-level security model; no additional per-service permissions are introduced at this stage.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can create a new service with multiple systems in under 3 minutes.
- **SC-002**: Users can find and view details of any service in the catalog within 10 seconds of navigating to the page.
- **SC-003**: All CRUD operations (create, read, update, delete) complete successfully and persist data across page reloads.
- **SC-004**: 95% of users can complete the service creation workflow on their first attempt without encountering confusion or errors.
- **SC-005**: The service catalog page loads and displays all services within the same performance envelope as existing pages (Tools, Resources).
