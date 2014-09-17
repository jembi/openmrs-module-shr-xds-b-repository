[![Build Status](https://travis-ci.org/jembi/openmrs-module-shr-xds-b-repository.svg)](https://travis-ci.org/jembi/openmrs-module-shr-xds-b-repository)

XDS.b Interface module for OpenMRS
==================================

A module to allow OpenMRS to act as an [XDS.b repository](http://wiki.ihe.net/index.php?title=Document_Repository).

Setup
-----

Firstly set the global property `xds-b-repository.xdsregistry.url` to the URL of the XDS registry that document should be registered with.

To allow the module to run correctly you need to add a log folder for send XDS message (messages to the registry).

`sudo mkdir /var/log/xdslog`

Then give permissions to the user running OpenMRS to write and create folders in that directory. The easiest way to get this right is as follow:

`sudo chmod 777 /var/log/xdslog`

You may restrict the permissions more if necessary.

Dependencies
------------

* Content Handler module: https://github.com/jembi/openmrs-module-shr-contenthandler

Usage
-----

This module provides a XDS.b repository endpoint at the following location:

`/openmrs/ms/xdsrepository`

It supports the 'provide and register document set' and the 'retrieve document set' transactions.
 
The module uses the content handler module to store documents. By default the content handler will store these to the file systems. If you would like to handle documents that are received you can register a content handler implementation of your own. See the content handler documentation.
   
This module keeps track of which documents map to which content handlers, it does this by mapping the document ID to a content handler class. If you would like to register a synthetic (or otherwise) document with the module it provides a module service to allow you to do so. This service will also register the document with the configured XDS.b registry.

Example usage of the service methods:

```Java
XDSbService xdsService = Context.getService(XDSbService.class);
xdsService.registerDocuments(contentHandlersMap, submitObjectRequest);
// or
xdsService.registerDocument(documentUniqueId, contentHandlerClass, submitObjectRequest);
```