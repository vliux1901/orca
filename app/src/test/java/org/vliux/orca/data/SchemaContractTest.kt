package org.vliux.orca.data

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaContractTest {
    @Test
    fun version1SchemaDefinesSourceForeignKeysForFactsAndServiceItems() {
        val schemaFile = sequenceOf(
            File("schemas/org.vliux.orca.data.OrcaDatabase/1.json"),
            File("app/schemas/org.vliux.orca.data.OrcaDatabase/1.json"),
        ).firstOrNull(File::exists) ?: File("schemas/org.vliux.orca.data.OrcaDatabase/1.json")
        assertTrue("Expected exported schema at ${schemaFile.path}", schemaFile.exists())

        val root = Json.parseToJsonElement(schemaFile.readText()).jsonObject
        val entities = root.getValue("database").jsonObject.getValue("entities").jsonArray

        val documentFacts = entities.first { entity ->
            entity.jsonObject.getValue("tableName").jsonPrimitive.content == "document_facts"
        }.jsonObject
        val serviceItems = entities.first { entity ->
            entity.jsonObject.getValue("tableName").jsonPrimitive.content == "service_items"
        }.jsonObject

        assertHasForeignKey(documentFacts, childColumn = "sourceId", parentTable = "document_sources", onDelete = "SET NULL")
        assertHasForeignKey(serviceItems, childColumn = "sourceId", parentTable = "document_sources", onDelete = "SET NULL")
        assertHasIndex(documentFacts, "index_document_facts_sourceId")
        assertHasIndex(serviceItems, "index_service_items_sourceId")
    }

    private fun assertHasForeignKey(
        entity: JsonObject,
        childColumn: String,
        parentTable: String,
        onDelete: String,
    ) {
        val matchingKey = entity.getValue("foreignKeys").jsonArray.firstOrNull { foreignKey ->
            val objectValue = foreignKey.jsonObject
            objectValue.getValue("table").jsonPrimitive.content == parentTable &&
                objectValue.getValue("onDelete").jsonPrimitive.content == onDelete &&
                objectValue.getValue("columns").jsonArray.single().jsonPrimitive.content == childColumn
        }
        assertTrue(
            "Expected foreign key from $childColumn to $parentTable with onDelete=$onDelete",
            matchingKey != null,
        )
    }

    private fun assertHasIndex(
        entity: JsonObject,
        indexName: String,
    ) {
        val indexNames = entity.getValue("indices").jsonArray.map { index ->
            index.jsonObject.getValue("name").jsonPrimitive.content
        }
        assertTrue("Expected index $indexName", indexNames.contains(indexName))
    }
}
