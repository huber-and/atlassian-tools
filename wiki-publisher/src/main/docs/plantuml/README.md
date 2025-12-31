# Wiki Publisher - PlantUML Diagrams

This directory contains PlantUML diagrams documenting the architecture, design, and behavior of the Wiki Publisher application.

## Diagrams Overview

### 1. **architecture.puml** - Architecture Overview
Provides a high-level view of the Wiki Publisher system components and their relationships:

- **Core Components**: Publisher, ConfluenceClient, Configuration
- **Data Models**: Page, Attachment
- **Processing Pipeline**: Parser (interface), AntoraParser, Transformer (interface), ConfluenceTransformer
- **External APIs**: PageApi, SpaceApi, ContentAttachmentsApi, ContentPropertiesApi

**Key Relationships**:
- Publisher orchestrates the publishing process
- ConfluenceClient delegates API interactions
- Parser converts source content to Page hierarchy
- Transformer converts HTML to Confluence storage format

### 2. **class_diagram.puml** - Class Diagram
Detailed class structure showing attributes, methods, and relationships:

**Key Classes**:
- **Publisher**: Main entry point, orchestrates the publishing workflow
- **Configuration**: Holds Confluence credentials and space mappings
- **Configuration.Mapper**: Maps local paths to Confluence spaces
- **ConfluenceClient**: Manages Confluence API interactions
- **Parser & AntoraParser**: Extracts page hierarchies from Antora documentation
- **Transformer & ConfluenceTransformer**: Converts content to Confluence format
- **Page**: Represents Confluence pages with hierarchical structure
- **Attachment**: Represents file attachments for pages

### 3. **sequence_diagram.puml** - Publishing Flow Sequence
Shows the detailed sequence of operations when publishing content:

1. **Initialization**: Publisher creates parser and client
2. **Configuration Loading**: Retrieves configured space mappers
3. **For Each Mapper**:
   - Parse documentation structure
   - Connect to Confluence API
   - Process root page (if configured)
   - For each page recursively:
     - Load content from source
     - Transform to Confluence format
     - Update page in Confluence
     - Set page properties
     - Upload attachments
     - Process child pages

### 4. **use_cases.puml** - Use Cases Diagram
Captures the main use cases and user interactions:

**Primary Use Cases**:
- Configure Publisher (URL, credentials, mappers)
- Parse Documentation (extract Antora pages)
- Transform Content (convert HTML to Confluence storage)
- Connect to Confluence (establish API connection)
- Create/Update Pages (publish to Confluence)
- Upload Attachments (attach files to pages)
- Set Page Properties (configure appearance)
- Handle Errors (error handling and logging)

### 5. **data_model.puml** - Data Model Diagram
Shows the data entities and their relationships:

**Key Entities**:
- **Configuration**: Contains URL, credentials, debug flag, and mappers
- **Mapper**: Maps local paths to Confluence spaces
- **Page**: Hierarchical page structure with parent-child relationships
- **Attachment**: Files attached to pages
- **TransformResult**: Output of content transformation
- **PageBulk**: Confluence API representation of pages
- **Version**: Page version tracking

### 6. **process_flow.puml** - Processing Flow Diagram
Activity diagram showing the step-by-step workflow:

1. Load and initialize configuration
2. Create Publisher instance with parser and client
3. Iterate through configured space mappers
4. Parse source documentation
5. Resolve page hierarchy
6. For each page (recursively):
   - Get or create page in Confluence
   - Load and transform content
   - Update page body
   - Upload attachments
   - Process child pages
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
- **Strategy Pattern**: Parser and Transformer interfaces allow pluggable implementations
- **Composite Pattern**: Page hierarchy uses parent-child relationships
- **Adapter Pattern**: Converts between different content formats
- **Template Method**: Standard publishing workflow with configurable behavior

## Configuration Flow

```
Configuration
  ├── URL: Confluence instance URL
  ├── Credentials: Username/Password
  ├── Debug: Dry-run mode flag
  └── Mappers: List of space mappings
      ├── spaceKey: Target Confluence space
      ├── root: Optional root page
      └── path: Local source directory
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
