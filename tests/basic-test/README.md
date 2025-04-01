# Basic test script for Wanaku

First, start by running Wanaku according to your preference.

Then, on the project base dir, install the tools (see the Makefile for details):

```shell
make test-tools 
```

To set up: 

```shell
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
``` 

First, activate the virtual environment if you haven't done so already.

```shell
source venv/bin/activate
```

To run the script, execute:

```shell
python3 basic-test.py
```