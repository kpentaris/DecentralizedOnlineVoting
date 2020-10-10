geth --datadir $1 init $2

geth --port 3000 --networkid 58343 --nodiscover --datadir=$1 --maxpeers=0 --rpc --rpcport 8543 --rpcaddr 0.0.0.0 --rpccorsdomain "*" --rpcapi "eth,net,web3,personal,miner" --allow-insecure-unlock --minerthreads "1" --ws --wsport 8544 --wsorigins "*" --wsaddr 0.0.0.0 & >> geth_log.log

coinbase=$(geth --exec "personal.newAccount('seed'); personal.unlockAccount(web3.eth.coinbase, 'seed', 0); console.log(web3.eth.coinbase); miner.start(1);" attach http://0.0.0.0:8543 | grep 0x)

node deploy.js $coinbase
