### Hyper Multicast VNC

A high performance Multicast VNC for massive screen sharing of a desktop.

by Choon Jin Ng

### Note

The Hyper Multicast VNC does not have built in VNC server. So you will have to install your own VNC server. Then get Hyper Multicast VNC to connect to the actual VNC server for streaming.

There are two parts to this program:
 - Server: Multicast the screen
 - Client: Receive and display the multicast stream and also to remotely control the server.

### Compilation instructions

- Include the following libraries when compiling:
	- jgroups-2.12.2.Final.jar
	- lib/jzlibsrc


### Running the server

Run Mvncviewer with the parameter HOST [VNC Server IP] PORT [VNC Server Port] MULTICAST [multicast address]:[multicast TTL] PROXYPORT [this server port number]

Example:

java vncviewer HOST 192.168.0.1 PORT 5900 MULTICAST 224.0.0.1:1 PROXYPORT 8888

### Running the client

Run Mvncviewer with the parameter HOST [Hyper Multicast Server IP] PORT [Hyper Multicast Server Port]

Example:

java vncviewer HOST 192.168.0.2 PORT 8888

### Contact

e-mail: cj at 5eejay dot com

### License

Please read the LICENSE file.
