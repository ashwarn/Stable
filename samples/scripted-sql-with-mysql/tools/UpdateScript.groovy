/*
 * Copyright 2015-2017 ForgeRock AS. All Rights Reserved
 *
 * Use of this code requires a commercial software license with ForgeRock AS.
 * or with one of its affiliates. All use shall be exclusively subject
 * to such license between the licensee and ForgeRock AS.
 */
package org.forgerock.openicf.connectors.hrdb

import groovy.sql.Sql
import org.forgerock.openicf.connectors.scriptedsql.ScriptedSQLConfiguration
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.Uid
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.common.objects.Attribute
import org.identityconnectors.framework.common.objects.AttributesAccessor
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptions

import java.sql.Connection

/**
 * Built-in accessible objects
 **/

// OperationType is UPDATE for this script
def operation = operation as OperationType

// The configuration class created specifically for this connector
def configuration = configuration as ScriptedSQLConfiguration

// Default logging facility
def log = log as Log

// Set of attributes describing the object to be updated
def updateAttributes = new AttributesAccessor(attributes as Set<Attribute>)

// The Uid of the object to be updated
def uid = uid as Uid

// The objectClass of the object to be updated, e.g. ACCOUNT or GROUP
def objectClass = objectClass as ObjectClass

/**
 * Script action - Customizable
 *
 * Update an object in the external source.  Connectors that do not support this should
 * throw an UnsupportedOperationException.
 *
 * This script should return the Uid of the updated object
 **/

/* Log something to demonstrate this script executed */
log.info("Create script, operation = " + operation.toString());

def connection = connection as Connection
def sql = new Sql(connection);
def ORG = new ObjectClass("organization")

switch (operation) {
    case OperationType.UPDATE:
        switch (objectClass) {
            case ObjectClass.ACCOUNT:
                sql.executeUpdate("""
                        UPDATE
                            users
                        SET
                            fullname = ?,
                            firstname = ?,
                            lastname = ?,
                            email = ?,
                            organization = ?,
                            password = coalesce(sha1(?), password),
                            timestamp = now()
                        WHERE
                            id = ?
                        """,
                        [
                                updateAttributes.findString("fullname"),
                                updateAttributes.findString("firstname"),
                                updateAttributes.findString("lastname"),
                                updateAttributes.findString("email"),
                                updateAttributes.findString("organization"),
                                updateAttributes.findString("password"),
                                uid.uidValue
                        ]
                );
                sql.executeUpdate("DELETE FROM car WHERE users_id=?",
                        [
                                uid.uidValue
                        ]
                );
                updateAttributes.findList("cars").each {
                    sql.executeInsert(
                            "INSERT INTO car (users_id,year,make,model) VALUES (?,?,?,?)",
                            [
                                    uid.uidValue,
                                    it.year,
                                    it.make,
                                    it.model
                            ]
                    )
                }
                break

            case ObjectClass.GROUP:
                sql.executeUpdate("""
                        UPDATE
                            groups
                        SET
                            description = ?,
                            gid = ?,
                            timestamp = now()
                        WHERE
                            id = ?
                        """,
                        [
                                updateAttributes.findString("description"),
                                updateAttributes.findString("gid"),
                                uid.uidValue
                        ]
                );
                sql.executeUpdate("DELETE FROM groups_users WHERE groups_id=?",
                        [
                                uid.uidValue
                        ]
                );
                updateAttributes.findList("users").each {
                    sql.executeInsert(
                            "INSERT INTO groups_users (users_id,groups_id) SELECT id,? FROM users WHERE uid=?",
                            [
                                    uid.uidValue,
                                    it.uid
                            ]
                    )
                }

                break

            case ORG:
                sql.executeUpdate("""
                        UPDATE
                            Organizations
                        SET
                            description = ?,
                            timestamp = now()
                        WHERE
                            id = ?
                        """,
                        [
                                updateAttributes.findString("description"),
                                uid.uidValue

                        ]
                );
                break

            default:
                uid.uidValue
        }
        return uid.uidValue
    case OperationType.ADD_ATTRIBUTE_VALUES:
        throw new UnsupportedOperationException(operation.name() + " operation of type:" +
                objectClass.objectClassValue + " is not supported.")
    case OperationType.REMOVE_ATTRIBUTE_VALUES:
        throw new UnsupportedOperationException(operation.name() + " operation of type:" +
                objectClass.objectClassValue + " is not supported.")
    default:
        throw new ConnectorException("UpdateScript can not handle operation:" + operation.name())
}
