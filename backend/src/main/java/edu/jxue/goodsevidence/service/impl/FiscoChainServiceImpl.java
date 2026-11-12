package edu.jxue.goodsevidence.service.impl;

import edu.jxue.goodsevidence.dto.EvidenceResult;
import edu.jxue.goodsevidence.service.ChainService;
import jakarta.annotation.PostConstruct;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.fisco.bcos.sdk.v3.transaction.manager.AssembleTransactionProcessor;
import org.fisco.bcos.sdk.v3.transaction.manager.TransactionProcessorFactory;
import org.fisco.bcos.sdk.v3.transaction.model.dto.CallResponse;
import org.fisco.bcos.sdk.v3.transaction.model.dto.TransactionResponse;
import org.fisco.bcos.sdk.v3.utils.Numeric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 区块链调用层实现（FISCO BCOS v3 Java SDK 3.6.0）—— 真实链版本
 *
 * <p>实现要点（都是踩坑换来的，改动前先读完）：</p>
 * <ol>
 *   <li><b>不要用底层 {@code client.sendTransaction(to, data, bool)}</b>。该方法的
 *       RPC 参数里第二个 String 会被当成 nodeId，传合约地址会报
 *       {@code -4010 no connection available for the node of the group}（还把合约地址
 *       显示成 node）。改用官方高层封装 {@link AssembleTransactionProcessor}，
 *       它负责 ABI 编解码、账户签名、等待回执。</li>
 *   <li><b>ABI 从链上部署产物里拿</b>（console 的 {@code contracts/.compiled/*.abi}），
 *       放在 resources/fisco/abi/ 下，用 InputStream 读取（fat jar 里没有 File）。</li>
 *   <li><b>交易"失败"不一定抛异常</b>：合约 require 回滚时 SDK 正常返回，
 *       必须检查 {@code returnCode != 0} 主动抛错，否则会把回滚当成成功。</li>
 *   <li>权属人用<b>业务ID字符串</b>（合约里是 string），不是 address —— 见 Ownership.sol 注释。</li>
 * </ol>
 */
@Service
@ConditionalOnProperty(name = "fisco.enabled", havingValue = "true")
public class FiscoChainServiceImpl implements ChainService {

    private static final Logger log = LoggerFactory.getLogger(FiscoChainServiceImpl.class);

    @Autowired
    private Client client;

    @Value("${fisco.group-id}")
    private String groupId;
    @Value("${fisco.contract.ownership-address}")
    private String ownershipAddress;
    @Value("${fisco.contract.evidence-address}")
    private String evidenceAddress;

    /** 合约 ABI（JSON 字符串） */
    private String ownershipAbi;
    private String evidenceAbi;
    /** 高层事务处理器：封装 ABI 编码 + 签名 + 回执 */
    private AssembleTransactionProcessor processor;
    /** 交易发起账户地址 */
    private String accountAddress;

    @PostConstruct
    public void init() throws Exception {
        ownershipAbi = readResource("fisco/abi/Ownership.abi");
        evidenceAbi = readResource("fisco/abi/Evidence.abi");
        processor = TransactionProcessorFactory.createAssembleTransactionProcessor(
                client, client.getCryptoSuite().getCryptoKeyPair());
        accountAddress = client.getCryptoSuite().getCryptoKeyPair().getAddress();
        log.info("[真链] 调用层初始化完成：group={}, 账户={}, 权属合约={}, 存证合约={}",
                groupId, accountAddress, ownershipAddress, evidenceAddress);
    }

    // -------------------- 业务实现 --------------------

    /** 权属上链初始化：Ownership.register(uint256 productId, string owner) */
    @Override
    public EvidenceResult registerOwnership(Long productId, String owner) throws Exception {
        TransactionResponse resp = processor.sendTransactionAndGetResponse(
                ownershipAddress, ownershipAbi, "register",
                Arrays.asList(BigInteger.valueOf(productId), owner));
        checkSuccess(resp, "权属登记");
        return toResult(resp, -1L);
    }

    /** 存证上链：Evidence.save(uint256 id, bytes32 hash, string bizType) -> bool */
    @Override
    public EvidenceResult saveEvidence(Long evidenceId, String bizType, String originHash) throws Exception {
        if (evidenceId == null || evidenceId <= 0) {
            throw new IllegalArgumentException("存证ID 必须由链下生成后传入，收到：" + evidenceId);
        }
        TransactionResponse resp = processor.sendTransactionAndGetResponse(
                evidenceAddress, evidenceAbi, "save",
                Arrays.asList(BigInteger.valueOf(evidenceId), hexToBytes32(originHash), bizType));
        checkSuccess(resp, "存证上链");
        return toResult(resp, evidenceId);
    }

    /** 权属转移：Ownership.transfer(uint256, string from, string to)，from 必须等于链上当前 owner */
    @Override
    public EvidenceResult transferOwnership(Long productId, String from, String to) throws Exception {
        TransactionResponse resp = processor.sendTransactionAndGetResponse(
                ownershipAddress, ownershipAbi, "transfer",
                Arrays.asList(BigInteger.valueOf(productId), from, to));
        checkSuccess(resp, "权属转移");
        return toResult(resp, -1L);
    }

    /** 校验存证：Evidence.verify(uint256 id, bytes32 hash) -> bool（view 方法，走 call 不上链） */
    @Override
    public boolean verifyEvidence(Long evidenceId, String originHash) throws Exception {
        CallResponse resp = processor.sendCall(
                accountAddress, evidenceAddress, evidenceAbi, "verify",
                Arrays.asList(BigInteger.valueOf(evidenceId), hexToBytes32(originHash)));
        if (resp.getReturnCode() != 0) {
            log.warn("[真链] 存证校验调用失败：{}", resp.getReturnMessage());
            return false;
        }
        List<Object> values = resp.getReturnObject();
        if (values == null || values.isEmpty()) {
            return false;
        }
        Object v = values.get(0);
        return v instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(v));
    }

    // -------------------- 内部工具 --------------------

    /** 合约 require 回滚时 SDK 不抛异常，只会把 returnCode 置非 0，必须主动检查 */
    private void checkSuccess(TransactionResponse resp, String action) {
        if (resp.getReturnCode() != 0) {
            throw new IllegalStateException(action + "失败（链上回滚）：" + resp.getReturnMessage());
        }
        TransactionReceipt receipt = resp.getTransactionReceipt();
        if (receipt != null && receipt.getStatus() != 0) {
            throw new IllegalStateException(action + "失败（回执状态=" + receipt.getStatus()
                    + "）：" + resp.getReturnMessage());
        }
    }

    private EvidenceResult toResult(TransactionResponse resp, long evidenceId) {
        EvidenceResult result = new EvidenceResult();
        result.setEvidenceId(evidenceId);
        TransactionReceipt receipt = resp.getTransactionReceipt();
        if (receipt != null) {
            result.setBlockNumber(receipt.getBlockNumber() == null ? 0L : receipt.getBlockNumber().longValue());
            result.setTxHash(receipt.getTransactionHash());
        }
        return result;
    }

    private String readResource(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** hex(0x 可省) -> 右对齐 32 字节 */
    private byte[] hexToBytes32(String hex) {
        String h = hex.startsWith("0x") ? hex.substring(2) : hex;
        byte[] raw = Numeric.hexStringToByteArray(h);
        if (raw.length == 32) {
            return raw;
        }
        byte[] out = new byte[32];
        System.arraycopy(raw, 0, out, 32 - raw.length, Math.min(raw.length, 32));
        return out;
    }
}
