// SPDX-License-Identifier: MIT
pragma solidity ^0.8.11;

/**
 * @title Evidence —— 哈希存证合约（对应论文「模块六：哈希上链存证」）
 * @notice 采用「链上存摘要、链下存原文」的设计：业务数据原文留存在 MySQL / MinIO，
 *         仅把 SHA-256 摘要写入链上。需要验真时重算摘要并调用 verify 比对，
 *         原文被篡改则哈希不一致，验真失败。
 *
 * @dev 存证ID 由**链下雪花算法生成后传入**（不用链上自增）：
 *      这样 chain_record.evidence_id 与链上键值完全一致，验真时可直接用业务主键反查，
 *      也避免了"合约自增ID 没取回来 → 全部写成 -1 → 主键冲突"这类问题。
 */
contract Evidence {

    struct Item {
        bytes32 hash;       // 业务数据摘要
        string  bizType;    // 业务类型 trade / quality / repair
        uint64  chainTime;  // 上链时间
        address sender;     // 上链发起方
    }

    /// 存证ID => 存证内容（ID 由链下生成并传入）
    mapping(uint256 => Item) private _evidences;
    /// 已存证条数（仅用于统计展示）
    uint256 private _count;

    event EvidenceSaved(uint256 indexed id, bytes32 hash, string bizType, uint64 chainTime);

    /**
     * @notice 保存存证摘要
     * @param id      存证ID（链下雪花算法生成，与 chain_record.evidence_id 一致）
     * @param hash    业务数据摘要（32 字节）
     * @param bizType 业务类型
     */
    function save(uint256 id, bytes32 hash, string memory bizType) public returns (bool) {
        require(id != 0, "invalid evidence id");
        require(hash != bytes32(0), "hash cannot be empty");
        require(_evidences[id].hash == bytes32(0), "evidence already exists");

        _count += 1;
        _evidences[id] = Item({
            hash: hash,
            bizType: bizType,
            chainTime: uint64(block.timestamp),
            sender: msg.sender
        });

        emit EvidenceSaved(id, hash, bizType, uint64(block.timestamp));
        return true;
    }

    /**
     * @notice 校验摘要是否与链上一致（对应「模块七：扫码验真」）
     */
    function verify(uint256 id, bytes32 hash) public view returns (bool) {
        return _evidences[id].hash != bytes32(0) && _evidences[id].hash == hash;
    }

    /// 查询存证详情
    function get(uint256 id) public view returns (bytes32 hash, string memory bizType, uint64 chainTime, address sender) {
        Item storage item = _evidences[id];
        require(item.hash != bytes32(0), "evidence not found");
        return (item.hash, item.bizType, item.chainTime, item.sender);
    }

    /// 当前存证总数
    function total() public view returns (uint256) {
        return _count;
    }
}
