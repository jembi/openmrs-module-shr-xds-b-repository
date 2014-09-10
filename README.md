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