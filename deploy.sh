#!/bin/bash

cd ansible
ansible-playbook playbook.yml "$@"
cd ..
