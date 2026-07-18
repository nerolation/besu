/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.AccountChanges;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.BalanceChange;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.NonceChange;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.SlotChanges;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.StorageChange;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DebugGetRawBlockAccessListTest {
  private final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();
  private DebugGetRawBlockAccessList method;
  private BlockchainQueries blockchainQueries;
  private Blockchain blockchain;

  @BeforeEach
  public void setUp() {
    blockchainQueries = mock(BlockchainQueries.class);
    blockchain = mock(Blockchain.class);

    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);

    method = new DebugGetRawBlockAccessList(blockchainQueries);
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo("debug_getRawBlockAccessList");
  }

  @Test
  public void shouldReturnRlpEncodedBlockAccessListForValidBlockNumber() {
    final long blockNumber = 5L;
    final BlockHeader header = blockDataGenerator.header(blockNumber);
    final Hash blockHash = header.getHash();

    when(blockchainQueries.headBlockNumber()).thenReturn(10L);
    when(blockchainQueries.getBlockHashByNumber(blockNumber)).thenReturn(Optional.of(blockHash));
    when(blockchainQueries.getBlockHeaderByHash(blockHash)).thenReturn(Optional.of(header));
    when(blockchainQueries.isBlockAccessListSupported(header)).thenReturn(true);

    final BlockAccessList blockAccessList = sampleBlockAccessList();
    when(blockchain.getBlockAccessList(blockHash)).thenReturn(Optional.of(blockAccessList));

    final JsonRpcResponse response = requestRawBlockAccessList(String.format("0x%X", blockNumber));

    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
    final BytesValueRLPOutput expected = new BytesValueRLPOutput();
    blockAccessList.writeTo(expected);
    assertThat(((JsonRpcSuccessResponse) response).getResult())
        .isEqualTo(expected.encoded().toHexString());
  }

  @Test
  public void shouldReturnEmptyListEncodingForEmptyBlockAccessList() {
    final BlockHeader header = blockDataGenerator.header(5L);
    final Hash blockHash = header.getHash();

    when(blockchainQueries.getBlockHeaderByHash(blockHash)).thenReturn(Optional.of(header));
    when(blockchainQueries.isBlockAccessListSupported(header)).thenReturn(true);
    when(blockchain.getBlockAccessList(blockHash))
        .thenReturn(Optional.of(new BlockAccessList(Collections.emptyList())));

    final JsonRpcResponse response = requestRawBlockAccessList(blockHash.toHexString());

    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
    assertThat(((JsonRpcSuccessResponse) response).getResult()).isEqualTo("0xc0");
  }

  @Test
  public void shouldReturnResourceNotFoundForUnknownBlockHash() {
    final Hash unknownHash =
        Hash.fromHexString("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

    when(blockchainQueries.getBlockHeaderByHash(unknownHash)).thenReturn(Optional.empty());

    final JsonRpcResponse response = requestRawBlockAccessList(unknownHash.toHexString());

    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);
    assertThat(((JsonRpcErrorResponse) response).getErrorType())
        .isEqualTo(RpcErrorType.RESOURCE_NOT_FOUND);
  }

  @Test
  public void shouldReturnResourceNotFoundForUnknownBlockNumber() {
    final long blockNumber = 5L;

    when(blockchainQueries.headBlockNumber()).thenReturn(10L);
    when(blockchainQueries.getBlockHashByNumber(blockNumber)).thenReturn(Optional.empty());
    when(blockchainQueries.getBlockHeaderByHash(Hash.EMPTY)).thenReturn(Optional.empty());

    final JsonRpcResponse response = requestRawBlockAccessList(String.format("0x%X", blockNumber));

    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);
    assertThat(((JsonRpcErrorResponse) response).getErrorType())
        .isEqualTo(RpcErrorType.RESOURCE_NOT_FOUND);
  }

  @Test
  public void shouldReturnResourceNotFoundForFutureBlockNumber() {
    when(blockchainQueries.headBlockNumber()).thenReturn(10L);

    final JsonRpcResponse response = requestRawBlockAccessList("0x64");

    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);
    assertThat(((JsonRpcErrorResponse) response).getErrorType())
        .isEqualTo(RpcErrorType.RESOURCE_NOT_FOUND);
  }

  @Test
  public void shouldReturnResourceNotFoundForPreAmsterdamBlocks() {
    final long blockNumber = 5L;
    final BlockHeader header = blockDataGenerator.header(blockNumber);
    final Hash blockHash = header.getHash();

    when(blockchainQueries.headBlockNumber()).thenReturn(10L);
    when(blockchainQueries.getBlockHashByNumber(blockNumber)).thenReturn(Optional.of(blockHash));
    when(blockchainQueries.getBlockHeaderByHash(blockHash)).thenReturn(Optional.of(header));
    when(blockchainQueries.isBlockAccessListSupported(header)).thenReturn(false);

    final JsonRpcResponse response = requestRawBlockAccessList(String.format("0x%X", blockNumber));

    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);
    assertThat(((JsonRpcErrorResponse) response).getErrorType())
        .isEqualTo(RpcErrorType.RESOURCE_NOT_FOUND);
  }

  @Test
  public void shouldReturnPrunedErrorWhenAccessListIsPruned() {
    final long blockNumber = 5L;
    final BlockHeader header = blockDataGenerator.header(blockNumber);
    final Hash blockHash = header.getHash();

    when(blockchainQueries.headBlockNumber()).thenReturn(10L);
    when(blockchainQueries.getBlockHashByNumber(blockNumber)).thenReturn(Optional.of(blockHash));
    when(blockchainQueries.getBlockHeaderByHash(blockHash)).thenReturn(Optional.of(header));
    when(blockchainQueries.isBlockAccessListSupported(header)).thenReturn(true);
    when(blockchain.getBlockAccessList(blockHash)).thenReturn(Optional.empty());

    final JsonRpcResponse response = requestRawBlockAccessList(String.format("0x%X", blockNumber));

    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);
    assertThat(((JsonRpcErrorResponse) response).getErrorType())
        .isEqualTo(RpcErrorType.PRUNED_HISTORY_UNAVAILABLE);
  }

  @Test
  public void shouldThrowInvalidJsonRpcParametersForInvalidParameter() {
    final JsonRpcRequestContext requestContext =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", method.getName(), new Object[] {"invalid"}));

    assertThatThrownBy(() -> method.response(requestContext))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessageContaining("Invalid block or block hash parameters (index 0)");
  }

  @Test
  public void shouldThrowInvalidJsonRpcParametersForMissingParameter() {
    final JsonRpcRequestContext requestContext =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", method.getName(), new Object[] {}));

    assertThatThrownBy(() -> method.response(requestContext))
        .isInstanceOf(InvalidJsonRpcParameters.class);
  }

  private JsonRpcResponse requestRawBlockAccessList(final String blockParameter) {
    return method.response(
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", method.getName(), new Object[] {blockParameter})));
  }

  private BlockAccessList sampleBlockAccessList() {
    final Address address = Address.fromHexString("0x1234567890123456789012345678901234567890");
    final StorageSlotKey slot = new StorageSlotKey(UInt256.ONE);

    final AccountChanges accountChanges =
        new AccountChanges(
            address,
            List.of(new SlotChanges(slot, List.of(new StorageChange(1, UInt256.valueOf(100))))),
            Collections.emptyList(),
            List.of(new BalanceChange(1, Wei.of(1000))),
            List.of(new NonceChange(1, 5L)),
            Collections.emptyList());

    return new BlockAccessList(List.of(accountChanges));
  }
}
