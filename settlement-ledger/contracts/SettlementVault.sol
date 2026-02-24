// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

contract SettlementVault {
    address public operator;
    mapping(uint256 => bool) public settled;
    mapping(address => int256) public balances;

    event Settled(uint256 indexed epochId);

    modifier onlyOperator() {
        require(msg.sender == operator, "ONLY_OPERATOR");
        _;
    }

    constructor(address operator_) {
        require(operator_ != address(0), "INVALID_OPERATOR");
        operator = operator_;
    }

    function settle(
        uint256 epochId,
        address[] calldata participants,
        int256[] calldata deltas
    ) external onlyOperator {
        require(!settled[epochId], "EPOCH_ALREADY_SETTLED");
        require(participants.length == deltas.length, "ARRAY_LENGTH_MISMATCH");

        int256 netSum = 0;
        for (uint256 i = 0; i < participants.length; i++) {
            balances[participants[i]] += deltas[i];
            netSum += deltas[i];
        }

        require(netSum == 0, "DELTAS_NOT_NET_ZERO");

        settled[epochId] = true;
        emit Settled(epochId);
    }
}
