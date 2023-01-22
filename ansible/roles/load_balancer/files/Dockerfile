FROM haproxy:2.5

USER root
RUN apt-get update && apt-get install -y \
  socat \
  sudo

COPY haproxy.cfg /usr/local/etc/haproxy/haproxy.cfg
