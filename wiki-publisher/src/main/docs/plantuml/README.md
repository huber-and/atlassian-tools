# Wiki Publisher - PlantUML Diagrams

This directory contains PlantUML diagrams documenting the architecture, design, and behavior of the Wiki Publisher application.

## Diagrams Overview

### 1. **architecture.puml** - Architecture Overview
Provides a high-level view of the Wiki Publisher system components and their relationships:

- **Core Components**: Publisher, ConfluenceClient, Configuration
- **Data Models**: Page, Attachment
- **Processing Pipeline**: Parser (interface), AntoraParser, LinkResolver, Transformer (interface), ConfluenceTransformer
- **External APIs**: PageApi, SpaceApi, ContentAttachmentsApi, ContentPropertiesApi

**Key Relationships**:
- Publisher orchestrates the publishing process
- Publisher resolves the Parser implementation named by `Configuration.parserClass`
- ConfluenceClient delegates API interactions
- Parser converts source content to Page hierarchy
- LinkResolver indexes the page trees of all mappers so internal links can be resolved
- Transformer converts HTML to Confluence storage format

### 2. **class_diagram.puml** - Class Diagram
Detailed class structure showing attributes, methods, and relationships:

**Key Classes**:
- **Publisher**: Main entry point, orchestrates the publishing workflow and resolves the parser
- **Configuration**: Holds Confluence credentials, the parser class name and space mappings
- **Configuration.Mapper**: Maps local paths to Confluence spaces
- **ConfluenceClient**: Manages Confluence API interactions and the orphan cleanup
- **Parser & AntoraParser**: Extracts page hierarchies from Antora documentation. The implementation is selected via `Configuration.parserClass` and needs a public no-argument constructor plus `init(Configuration)`
- **LinkResolver & Target & Kind**: Resolves an href to a page, anchor, attachment, external or unresolved target
- **Transformer & ConfluenceTransformer**: Converts content to Confluence format
- **Page**: Represents Confluence pages with hierarchical structure
- **Attachment**: Represents file attachments for pages

### 3. **sequence_diagram.puml** - Publishing Flow Sequence
Shows the detailed sequence of operations when publishing content:

1. **Initialization**: Publisher resolves the parser class without initializing it, verifies it implements `Parser`, instantiates it and calls `init(config)`
2. **Configuration Loading**: Retrieves configured space mappers
3. **Phase 1 — parse every mapper**: Parse documentation structure and log the page hierarchy
4. **Link Index**: Combine the page trees of all mappers, then create transformer and client. Cross-space links can only be resolved once all page trees are known
5. **Phase 2 — publish every mapper**:
   - Connect to Confluence API
   - Process root page (if configured)
   - For each page recursively:
     - Load content from source
     - Resolve internal links and transform to Confluence format
     - Update page in Confluence
     - Set page properties
     - Upload attachments
     - Process child pages
   - Report orphaned pages and — unless `deleteOrphans` is disabled — move them to the trash

### 4. **use_cases.puml** - Use Cases Diagram
Captures the main use cases and user interactions:

**Primary Use Cases**:
- Configure Publisher (URL, credentials, mappers)
- Plug In Custom Parser (replace the built-in Antora parser)
- Parse Documentation (extract Antora pages)
- Resolve Internal Links (rewrite hrefs across mappers and spaces)
- Transform Content (convert HTML to Confluence storage)
- Connect to Confluence (establish API connection)
- Create/Update Pages (publish to Confluence)
- Upload Attachments (attach files to pages)
- Set Page Properties (configure appearance)
- Remove Orphaned Pages (trash pages that no longer exist locally)
- Handle Errors (error handling and logging)

### 5. **data_model.puml** - Data Model Diagram
Shows the data entities and their relationships:

**Key Entities**:
- **Configuration**: Contains URL, credentials, debug flag, parser class name, and mappers
- **Mapper**: Maps local paths to Confluence spaces
- **MapperPages**: A parsed mapper — its space key, source path, root title and page tree
- **Target**: A resolved link target with its kind, title, space key, anchor or file
- **Page**: Hierarchical page structure with parent-child relationships
- **Attachment**: Files attached to pages
- **TransformResult**: Output of content transformation
- **PageBulk**: Confluence API representation of pages
- **Version**: Page version tracking

### 6. **process_flow.puml** - Processing Flow Diagram
Activity diagram showing the step-by-step workflow:

1. Load and initialize configuration
2. Create Publisher instance, resolving the parser named by `parserClass`
3. Phase 1 — for every space mapper: parse source documentation and resolve the page hierarchy
4. Build the link index from all page trees, then create transformer and client
5. Phase 2 — for every space mapper, for each page (recursively):
   - Get or create page in Confluence
   - Load and transform content, resolving internal links
   - Update page body
   - Upload attachments
   - Process child pages
6. Clean up orphaned pages per mapper
7. Handle any errors and continue

## Usage

These diagrams can be rendered using:
- **PlantUML Editor**: http://www.plantuml.com/plantuml/uml/
- **VS Code Extensions**: PlantUML extension
- **Command Line**: `plantuml *.puml`
- **CI/CD Pipelines**: Generate SVG/PNG during build

### Example: Generate all diagrams as PNG
```bash
plantuml -tpng "*.puml"
```

### Example: Generate specific diagram as SVG
```bash
plantuml -tsvg architecture.puml
```

## Architecture Summary

**Wiki Publisher** is a Java application that:

1. **Parses** Antora-generated documentation
2. **Transforms** HTML content to Confluence storage format
3. **Publishes** pages and attachments to Confluence spaces
4. **Manages** page hierarchies and properties

**Key Design Patterns**:
- **Strategy Pattern**: Parser and Transformer interfaces allow pluggable implementations; the Parser is selectable at runtime via `Configuration.parserClass`
- **Composite Pattern**: Page hierarchy uses parent-child relationships
- **Adapter Pattern**: Converts between different content formats
- **Template Method**: Standard publishing workflow with configurable behavior

## Configuration Flow

```
Configuration
  ├── URL: Confluence instance URL
  ├── Credentials: Username/Password
  ├── Debug: Dry-run mode flag
  ├── ParserClass: Parser implementation to use (defaults to AntoraParser)
  └── Mappers: List of space mappings
      ├── spaceKey: Target Confluence space
      ├── root: Optional root page
      ├── path: Local source directory
      └── deleteOrphans: Trash pages that no longer exist locally
```

## Page Hierarchy

```
Root Pages
  ├── Child Page 1
  │   ├── Grandchild 1
  │   └── Grandchild 2
  └── Child Page 2
      └── Attachments
```

## API Integration

The ConfluenceClient integrates with Confluence REST APIs:
- **v1 API**: ContentAttachmentsApi (for file uploads)
- **v2 API**: PageApi, SpaceApi, ContentPropertiesApi

## Error Handling

Errors are logged and do not stop the publishing process:
- Configuration errors
- Parsing errors
- Transformation errors
- API communication errors
- Attachment upload failures

Each mapper is processed independently to ensure partial failures don't block other spaces.
