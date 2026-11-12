// SPDX-License-Identifier: MIT
pragma solidity ^0.8.11;

/**
 * @title Ownership —— 商品权属合约（对应论文「模块四：智能合约权属转移」）
 * @notice 链上维护「商品ID -> 权属人」状态机。权属人用**业务侧用户/企业ID**（字符串）表示，
 *         而不是 address：本系统的账号体系在链下（MySQL + JWT），若链上用 address
 *         就必须给每个企业/用户分别生成并托管密钥对，对本科毕设而言过重且不安全。
 *
 *         【核心校验】transfer 时要求调用方声明的 from 必须等于链上当前 owner
 *         （用 keccak256 比较字符串），不等则整笔交易回滚。这样即使有人绕过链下系统
 *         直接调合约，也无法把别人的商品转走；身份真实性由链下 JWT 保证，
 *         权属状态一致性由合约保证，二者形成"链下认证 + 链上校验"的双重保障。
 *
 * @dev 部署顺序：先部署本合约，再把地址填到 application.yml 的 fisco.contract.ownership-address
 */
contract Ownership {

    struct OwnershipRecord {
        string  owner;              // 当前权属人（业务侧用户/企业ID）
        uint64  registerTime;       // 权属上链时间
        uint64  lastTransferTime;   // 最近一次转移时间
        bool    exists;             // 是否已登记
    }

    /// 商品ID => 权属记录
    mapping(uint256 => OwnershipRecord) private _records;

    event OwnershipRegistered(uint256 indexed productId, string owner, uint64 registerTime);
    event OwnershipTransferred(uint256 indexed productId, string from, string to, uint64 time);

    /**
     * @notice 权属上链初始化，商品登记成功后调用
     * @param productId 商品ID（与链下 product.product_id 对应）
     * @param owner     初始权属人（业务侧用户/企业ID）
     */
    function register(uint256 productId, string calldata owner) public returns (bool) {
        require(bytes(owner).length > 0, "invalid owner");
        require(!_records[productId].exists, "product already registered");

        _records[productId] = OwnershipRecord({
            owner: owner,
            registerTime: uint64(block.timestamp),
            lastTransferTime: 0,
            exists: true
        });

        emit OwnershipRegistered(productId, owner, uint64(block.timestamp));
        return true;
    }

    /**
     * @notice 权属转移，【核心校验】from 必须与链上当前 owner 一致
     * @param productId 商品ID
     * @param from      出让方（链下已由 JWT 认证过的当前持有者）
     * @param to        受让方
     */
    function transfer(uint256 productId, string calldata from, string calldata to) public returns (bool) {
        OwnershipRecord storage record = _records[productId];

        require(record.exists, "product not registered");
        require(
            keccak256(bytes(record.owner)) == keccak256(bytes(from)),
            "caller is not the owner"
        );
        require(bytes(to).length > 0, "invalid receiver");
        require(
            keccak256(bytes(to)) != keccak256(bytes(record.owner)),
            "receiver is already the owner"
        );

        string memory oldOwner = record.owner;
        record.owner = to;
        record.lastTransferTime = uint64(block.timestamp);

        emit OwnershipTransferred(productId, oldOwner, to, uint64(block.timestamp));
        return true;
    }

    /// 查询当前权属人
    function ownerOf(uint256 productId) public view returns (string memory) {
        require(_records[productId].exists, "product not registered");
        return _records[productId].owner;
    }

    /// 查询权属是否已上链
    function exists(uint256 productId) public view returns (bool) {
        return _records[productId].exists;
    }
}
