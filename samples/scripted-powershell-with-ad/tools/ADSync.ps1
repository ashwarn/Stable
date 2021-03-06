#
# Copyright 2014-2017 ForgeRock AS. All Rights Reserved
#
# Use of this code requires a commercial software license with ForgeRock AS.
# or with one of its affiliates. All use shall be exclusively subject
# to such license between the licensee and ForgeRock AS.
#
#REQUIRES -Version 2.0

<#  
.SYNOPSIS  
    This is a sample Sync script for Active Directory users and groups
	
.DESCRIPTION
	This script leverages the uSNChanged attribute to find entries that have been
	modified since last invoke
	
.INPUT VARIABLES
	The connector injects the following variables to the script:
	- <prefix>.Configuration : handler to the connector's configuration object
	- <prefix>.Options: a handler to the Operation Options
	- <prefix>.Operation: String correponding to the operation ("SYNC" or "GET_LATEST_SYNC_TOKEN" here)
	- <prefix>.ObjectClass: the Object class object (__ACCOUNT__ / __GROUP__ / other)
	- <prefix>.Token: The sync token value

.RETURNS
	if Operation is "GET_LATEST_SYNC_TOKEN":
	Must return an object representing the last known sync token for the corresponding ObjectClass
	
	if Operation is "SYNC":
    Call Connector.Results.Process(Hashtable) describing one update:
    Map should look like the following:
   [
   "SyncToken": <Object> token of the object that changed(could be Integer, Date, String) , [!! could be null]
   "DeltaType": <String> ("CREATE|UPDATE|CREATE_OR_UPDATE"|"DELETE"), the type of change that occurred
   "Uid": <String>; the Uid (OpenICF __UID__) of the entry
   "PreviousUid": <String>, the Uid of the object before the change (This is for rename ops)
   "Object": <Hashtable> The Connector object that has changed
   "ObjectClass": <String|ObjectClass> The ObjectClass of the entry. Needs to be set if op=DELETE and Object=null
   ]
  
.NOTES  
    File Name      : ADSync.ps1  
    Author         : Gael Allioux (gael.allioux@forgerock.com)
    Prerequisite   : PowerShell V2 - AD module loaded by the connector
    Copyright 2014 - ForgeRock AS    

.LINK  
    Script posted over:  
    http://openicf.forgerock.org
		
	Active Directory Administration with Windows PowerShell
	http://technet.microsoft.com/en-us/library/dd378937(v=ws.10).aspx
#>

# We need this global Boolean to handle the case where the sync handler returns false 
# and we don't want to break the pipe because of https://bugster.forgerock.org/jira/browse/OPENIDM-2650
$proceed = $TRUE

# We define a filter to process results through a pipe and feed the sync result handler
filter Process-Sync {
	if ($proceed)
	{
		$object = @{"__NAME__" = $_.DistinguishedName; "__UID__" = $_.ObjectGUID.ToString();}
		foreach($attr in $_.GetEnumerator())
		{
			if ($attr.Value.GetType().Name -eq "ADPropertyValueCollection")
			{
				$values = @();
				foreach($val in $attr.Value) 
				{
					$values += $val
				}
				$object.Add($attr.Key, $values)
			}
			else
			{
				$object.Add($attr.Key, $attr.Value)
			}
		}
		
		$result = @{"SyncToken" = $_.uSNChanged; "DeltaType" = "CREATE_OR_UPDATE"; "Uid" = $_.ObjectGUID.ToString(); "Object" = $object}
		
		$proceed = $Connector.Result.Process($result)
	}
}

try
{
if ($Connector.Operation -eq "GET_LATEST_SYNC_TOKEN")
{
	# we should specify a server since USN is specific a DC instance
	# For that, Get-ADRootDSE has a -Server option
	$dse = Get-ADRootDSE
	$token = New-Object Org.IdentityConnectors.Framework.Common.Objects.SyncToken($dse.highestCommittedUSN);
	$Connector.Result.SyncToken = $token;
}
elseif ($Connector.Operation -eq "SYNC")
{
	$searchBase = $Connector.Configuration.PropertyBag.baseContext
	$attrsToGet = "*"
	$filter = "uSNChanged -gt {0}" -f $Connector.Token
	
	switch ($Connector.ObjectClass.Type)
	{
		"__ACCOUNT__" 	
		{
			Get-ADUser -Filter $filter -SearchBase $searchBase -Properties $attrsToGet | Process-Sync
		}
		"__GROUP__"
		{
			Get-ADGroup -Filter $filter -SearchBase $searchBase -Properties $attrsToGet | Process-Sync
		}
		default {throw "Unsupported type: $($Connector.ObjectClass.Type)"}
	}
}
else
{
	throw New-Object Org.IdentityConnectors.Framework.Common.Exceptions.ConnectorException("SyncScript can not handle operation: $($Connector.Operation)")
}
}
catch #Rethrow the original exception
{
	throw
}
