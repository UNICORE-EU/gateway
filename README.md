# UNICORE Gateway

The UNICORE Gateway is an (optional) server component that
provides a reverse https proxy, allowing you to run several backend
servers (UNICORE/X, Registery, ...) behind a single address.
This helps with firewall configuration, requiring only a single open ports.

(a similar effect can be achieved using other http servers that can
act as a reverse proxy, such as Apache httpd or nginx)


The Gateway is distributed as part of the Core Server bundle.
