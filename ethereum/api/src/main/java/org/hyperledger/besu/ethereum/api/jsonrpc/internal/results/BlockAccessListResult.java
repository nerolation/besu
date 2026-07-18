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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.AccountChanges;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.BalanceChange;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.CodeChange;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.NonceChange;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.SlotChanges;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.StorageChange;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * JSON result for a block access list (EIP-7928), serialized as a bare array of account changes as
 * defined by the execution-apis schema for eth_getBlockAccessList.
 */
public class BlockAccessListResult {

  private final List<AccountChangesResult> accountChanges;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public BlockAccessListResult(final List<AccountChangesResult> accountChanges) {
    this.accountChanges = accountChanges == null ? List.of() : accountChanges;
  }

  public static BlockAccessListResult fromBlockAccessList(final BlockAccessList list) {
    return new BlockAccessListResult(
        list.accountChanges().stream().map(AccountChangesResult::new).toList());
  }

  @JsonValue
  public List<AccountChangesResult> getAccountChanges() {
    return accountChanges;
  }

  public static class AccountChangesResult {
    public final String address;
    public final List<SlotChangeResult> storageChanges;
    public final List<String> storageReads;
    public final List<BalanceChangeResult> balanceChanges;
    public final List<NonceChangeResult> nonceChanges;
    public final List<CodeChangeResult> codeChanges;

    public AccountChangesResult(final AccountChanges changes) {
      this.address = changes.address().toString();
      this.storageChanges = changes.storageChanges().stream().map(SlotChangeResult::new).toList();
      this.storageReads = changes.storageReads().stream().map(sr -> slotKeyHex(sr.slot())).toList();
      this.balanceChanges =
          changes.balanceChanges().stream().map(BalanceChangeResult::new).toList();
      this.nonceChanges = changes.nonceChanges().stream().map(NonceChangeResult::new).toList();
      this.codeChanges = changes.codeChanges().stream().map(CodeChangeResult::new).toList();
    }
  }

  public static class SlotChangeResult {
    public final String key;
    public final List<StorageChangeResult> changes;

    public SlotChangeResult(final SlotChanges changes) {
      this.key = slotKeyHex(changes.slot());
      this.changes = changes.changes().stream().map(StorageChangeResult::new).toList();
    }
  }

  public static class StorageChangeResult {
    public final String index;
    public final String value;

    public StorageChangeResult(final StorageChange change) {
      this.index = Quantity.create(change.txIndex());
      this.value = change.newValue().toHexString();
    }
  }

  public static class BalanceChangeResult {
    public final String index;
    public final String value;

    public BalanceChangeResult(final BalanceChange change) {
      this.index = Quantity.create(change.txIndex());
      this.value = change.postBalance().toShortHexString();
    }
  }

  public static class NonceChangeResult {
    public final String index;
    public final String value;

    public NonceChangeResult(final NonceChange change) {
      this.index = Quantity.create(change.txIndex());
      this.value = Quantity.create(change.newNonce());
    }
  }

  public static class CodeChangeResult {
    public final String index;
    public final String code;

    public CodeChangeResult(final CodeChange change) {
      this.index = Quantity.create(change.txIndex());
      this.code = change.newCode().toHexString();
    }
  }

  /**
   * Well-formed block access lists always carry the slot key preimage (the RLP encoder relies on
   * it); fall back to the slot hash in the pathological case where it is absent.
   */
  private static String slotKeyHex(final StorageSlotKey slot) {
    return slot.getSlotKey()
        .map(UInt256::toHexString)
        .orElseGet(() -> slot.getSlotHash().toHexString());
  }
}
