#!/bin/bash

# run from the script directory
cd "$(dirname "$0")"

replaceable=0
port=0

while [ $# -gt 0 ]
do
    case "$1" in
        -replaceable) replaceable=1;;
        -port) port="$2"; shift;;
        *) echo >&2 \
            "usage: $0 [-replaceable] [-port 10123]"
            exit 1;;
    esac
    shift
done
  
if ! [[ $port =~ ^-?[0-9]+$ ]]; then
    echo "Port value should be numeric"
    exit 1
fi

if [ \( $port -lt 0 \) -o \( $port -gt 65535 \) ]; then
    echo "Port value out of range 0-65535"
    exit 1
fi

# Now write the configuration file
if [ ! -d "run/config" ]; then
  mkdir run/config
fi
echo "# Configuration file
# Autogenerated from command-line options

malmoports {
  I:portOverride=$port
}
" > run/config/malmomodCLIENT.cfg

if [ $replaceable -gt 0 ]; then
    echo "runtype {
  B:replaceable=true
}
" >> run/config/malmomodCLIENT.cfg
fi

# Finally we can launch the Mod, which will load the config file
while
    ./gradlew setupDecompWorkspace
    ./gradlew build
    ./gradlew runClient
    [ $replaceable -gt 0 ]
do :; done
