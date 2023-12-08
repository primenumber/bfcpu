#!/bin/sh

od -A n -t x1 -w1 -v $1 | cut -c 2- > $1.hex
echo '00' >> $1.hex
