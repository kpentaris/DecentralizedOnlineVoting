# README

## Voting system setup

### Setting up the Blockchain

Setting up Ethereum RPC

 - Download Geth 1.9.8 https://geth.ethereum.org/downloads/
 - Install
 - Create project dir
 - Create genesis.json file
 - Write the following json inside:
    {
		"config": {
		"chainId": 143,
		"homesteadBlock": 0,
		"eip150Block": 0,
		"eip155Block": 0,
		"eip158Block": 0,
		"byzantiumBlock": 0,
		"constantinopleBlock": 0, 
		"petersburgBlock":0
		},
		"alloc": {},
		"difficulty" : "0x10000",
		"gasLimit"   : "0x88800000"
	}
 - mkdir "blockchain" inside the project dir
 - run: geth --datadir blockchain init genesis.json
 - this will create the genesis block
 - To start the geth service run:
	`geth --port 3000 --networkid 58343 --nodiscover --datadir=./blockchain --maxpeers=0 --rpc --rpcport 8543 --rpcaddr 0.0.0.0 --rpccorsdomain "*" --rpcapi "eth,net,web3,personal,miner" --allow-insecure-unlock --minerthreads "1" --ws --wsport 8544 --wsorigins "*" --wsaddr 0.0.0.0`
 - Open new terminal and run: `geth attach http://localhost:8543`
 - On JS console create an account running:
   `personal.newAccount("seed")`
   `personal.unlockAccount(web3.eth.coinbase, "seed", 0)`
 - Start mining by running: miner.start(1)
 
Alternatively run the master_script.sh where the first argument is the data dir and the second argument is the genesis.json file:
./master_script.sh /path/to/blockchain /path/to/genesis.json


### Java Smart Contract wrappers 

To generate the Java smart contract class wrappers you require the .abi and .bytecode files of the smart contract (which can be generated from remix
.ethereum.org). These files, along with the .sol source code file must be under src/main/solidity directory.
Once ready, run the following command `mvn web3j:generate-sources`

If your smart contract has very high solidity version (higher than 4.14) then the maven generation might not work. In this case installing the
 command-line tools (https://docs.web3j.io/command_line_tools/) for web3j and running the following command:
 
 `web3j solidity generate -a=./src/main/solidity/VotingContract.abi -b=./src/main/solidity/VotingContract.bytecode -o=./src/main/java/edu/pentakon/votingapp -p=contract`

will generate the Java contract wrappers.

### Using the contract wrappers

In order to use the Java contract wrappers, a Geth client must be running and the account used must be *unlocked*. This can be done using the method
described above using the javascript attached console. Also for any contract mutation the user must have started mining and also have mined some Ethereum
first in order to have enough Wei for Gas.

### Properly initialize the Ethereum Service
In the EthereumService.java, initialize the 4 static variables with the proper values according to how the Ethereum blockchain is set up. The
 wallet password field is the value `seed` used creating the new account via JS console. The wallet file path is the path of the wallet file inside
  the **blockchain/keystore** directory.
