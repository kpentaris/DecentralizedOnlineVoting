pragma solidity >=0.5.10;
pragma experimental ABIEncoderV2;

contract VotingContract {

  struct Vote {
    string uid; // user id
    string ballot; // serialized ballot which contains encrypted vote, the vote timestamp, the vote proof of validity and the uid
    uint256 timestamp; // Unix timestamp of the vote. Must match the one in the ballot
    string signature; // ballot signature. The serialized ballot value must be validated by this signature using the uid
  }

  address electionAdministrator;

  // Voting parameters
  string public electionTitle;
  string public allowedUIDs = "17254342489730536923303490930541977734358823547980063370201237678712604655134800238955963599888654259982571990529689236459372612176071049668628710280078256531870917741269844985368018183286912729624022999834855715372077746548880405230312248728342872664232247162839940071486056038967459547344325482370332413221;17719365530085763457195868871157233169952337536135777959495889174227153763058575096665694907123232973087358203996464872878876407937677129733169764158849276307495150914105135891314645476992321291318977731677534116082379808040215377701818418808985241072091463970432499155945297790713529072171955782261316050955;3791514487605918977338254699151274508480356711912381792932507184943312530350837533817567754284658891058914298253087759836565705429537723576513469283086122416204804034726050603296263777976370972267125602013056359871765510535112703129942961129917235684611023057638845051236857431182892061936949918221862796117";
  uint64 public allowedUIDCount = 3;
  uint256 public starTimestamp;
  uint256 public voteSubmitEndTS;
  string public cyclicGroupPrime;

  mapping(string => Vote) public votes; // uid => vote
  // Multi-party computation secret shares
  // The map key is a participant's UID and the value is an array of encrypted
  // MPC shares that need to be summed and posted in the mpcSums (again, uid => sum)
  mapping(string => string[]) public mpcShares;
  mapping(string => string) public mpcSums;
  string[] mpcSubmissions;

  // Upon contract deployment, all voting parameters are initialized and cannot be changed
  constructor () public {
    electionAdministrator = msg.sender;
  }

  function updateElectionParameters(uint256 _start, uint256 _voteEnd, string memory _title, string memory _prime) public {
    require(electionAdministrator == msg.sender, "Only the administrator can update the election parameters.");
    require(_start < _voteEnd, "The provided timestamps are not correct.");
    starTimestamp = _start;
    voteSubmitEndTS = _voteEnd;
    electionTitle = _title;
    cyclicGroupPrime = _prime;
  }

  function getChainTS() public view returns (uint256) {
    return now;
  }

  function submitVote(string memory uid, string memory ballot, string memory signature, string[] memory uids, string[] memory shares) public {
    require(now >= starTimestamp && now < voteSubmitEndTS, "Cannot submit vote outside of the defined voting time window.");
    require(uids.length == allowedUIDCount, "MPC shares count does not match uid count.");

    votes[uid] = Vote({
      uid : uid, ballot : ballot, signature : signature, timestamp : now
      });

    for (uint8 idx = 0; idx < uids.length; idx++) {
      string memory id = uids[idx];
      string memory share = shares[idx];
      mpcShares[id].push(share);
    }
  }

  function submitMPCShareSum(string memory uid, string memory sum) public {
    require(now >= voteSubmitEndTS, "Cannot submit mpc share sum before voting has ended.");
    mpcSums[uid] = sum;
    mpcSubmissions.push(uid);
  }

  function tallyVotes() public view returns (bool) {
    return mpcSubmissions.length >= allowedUIDCount;
  }

  function endElection() public {
    require(electionAdministrator == msg.sender, "Only the administrator can end the election prematurely.");
    voteSubmitEndTS = now;
  }

  function testDeployment() public pure returns (string memory) {
    return "Success!";
  }

  function compareStrings(string memory a, string memory b) private pure returns (bool) {
    return (keccak256(abi.encodePacked((a))) == keccak256(abi.encodePacked((b))));
  }

}
