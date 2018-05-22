/*
 * Copyright 2014-2017 ForgeRock AS. All Rights Reserved
 *
 * Use of this code requires a commercial software license with ForgeRock AS.
 * or with one of its affiliates. All use shall be exclusively subject
 * to such license between the licensee and ForgeRock AS.
 */

import org.forgerock.json.resource.Connection
import org.forgerock.openicf.connectors.scriptedcrest.ScriptedCRESTConfiguration
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.logging.Log

def operation = operation as OperationType
def configuration = configuration as ScriptedCRESTConfiguration
def connection = connection as Connection
def log = log as Log

def file = new File(System.getProperty("launcher.project.location") + "/conf/provisioner.openicf-scriptedcrest.json")

def schema = SchemaSlurper.parse(file.toURI().toURL())

configuration.propertyBag.putAll(schema)

builder.schema({
    schema.each { key, value ->
        objectClass {
            type key
            value.attributes.values().each {
                attribute it.attributeInfo
            }
        }
    }
})
