NetSonar v1.0.4
- Devices tab starts scanning automatically
- Extra big SCAN / STOP button under the radar
- Live scan log on the Devices screen (shows every step), version shown in header
- Scan runs off the UI thread; debug builds now update over each other
- Device discovery: also reads Android's neighbour (ARP) table → finds silent devices and shows their MAC addresses
- Small diagnostic line under the radar after each scan (interface, IPs checked, ping/TCP replies)
- Includes all v1.0.1 fixes: readable colours (dark + light), round SCAN button, faster scan, full-detail device list
