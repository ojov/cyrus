"use client";

import { useCallback, useEffect, useState } from "react";
import { beneficiaryApi, type BankItem, type BeneficiaryItem } from "@/lib/api";

const field =
  "w-full rounded-lg border border-border bg-muted px-3 py-2 text-sm outline-none focus:border-primary";

export default function BeneficiariesPage() {
  const [items, setItems] = useState<BeneficiaryItem[]>([]);
  const [banks, setBanks] = useState<BankItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [loadingBanks, setLoadingBanks] = useState(false);

  // Add-a-beneficiary form: enter account number → pick a bank (searchable) → we verify the account
  // and show the resolved name → confirm to add. No nickname — the label IS the verified name.
  const [accountNumber, setAccountNumber] = useState("");
  const [bankSearch, setBankSearch] = useState("");
  const [showBankList, setShowBankList] = useState(false);
  const [selectedBank, setSelectedBank] = useState<BankItem | null>(null);
  const [verifiedName, setVerifiedName] = useState<string | null>(null);
  const [verifying, setVerifying] = useState(false);
  const [creating, setCreating] = useState(false);

  const load = useCallback(async () => {
    try {
      const res = await beneficiaryApi.list();
      setItems(res.data ?? []);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load beneficiaries");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    Promise.resolve().then(load);
  }, [load]);

  // Fetch the (server-cached) bank list once the account number is a full NUBAN.
  useEffect(() => {
    if (accountNumber.trim().length < 10 || banks.length > 0) return;
    Promise.resolve().then(() => setLoadingBanks(true));
    beneficiaryApi
      .listBanks()
      .then((res) => setBanks(res.data ?? []))
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load bank list"))
      .finally(() => setLoadingBanks(false));
  }, [accountNumber, banks.length]);

  // Verify the account whenever a bank is selected against a full account number. Runs in an effect
  // with a cancelled flag so a stale response (the user changed the account/bank while a verify was
  // in flight) is discarded — otherwise it could set verifiedName after selectedBank was reset,
  // enabling the Add button while create() silently no-ops.
  useEffect(() => {
    if (!selectedBank || accountNumber.trim().length < 10) return;
    let cancelled = false;
    Promise.resolve().then(() => {
      if (!cancelled) setVerifying(true);
    });
    beneficiaryApi
      .verify(accountNumber.trim(), selectedBank.code)
      .then((res) => {
        if (!cancelled) setVerifiedName(res.data.accountName);
      })
      .catch((e) => {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : "Couldn't verify this account. Check the number and bank.");
          setSelectedBank(null);
        }
      })
      .finally(() => {
        if (!cancelled) setVerifying(false);
      });
    return () => {
      cancelled = true;
    };
  }, [accountNumber, selectedBank]);

  const filteredBanks = bankSearch.trim()
    ? banks.filter((b) => b.name.toLowerCase().includes(bankSearch.trim().toLowerCase()))
    : banks;

  function onAccountChange(value: string) {
    setAccountNumber(value);
    // Any account-number change invalidates a prior bank selection / verification (the verify effect
    // above re-runs; its cleanup cancels any in-flight request so a stale name can't land).
    setSelectedBank(null);
    setVerifiedName(null);
    setBankSearch("");
    setError(null);
  }

  function selectBank(bank: BankItem) {
    // Synchronous — the verify effect above fires off the selectedBank change.
    setSelectedBank(bank);
    setBankSearch(bank.name);
    setShowBankList(false);
    setVerifiedName(null);
    setError(null);
  }

  async function create() {
    if (!selectedBank || !verifiedName) return;
    setCreating(true);
    setError(null);
    try {
      await beneficiaryApi.create({
        accountNumber: accountNumber.trim(),
        bankCode: selectedBank.code,
        bankName: selectedBank.name,
      });
      setAccountNumber("");
      setBankSearch("");
      setSelectedBank(null);
      setVerifiedName(null);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to add beneficiary");
    } finally {
      setCreating(false);
    }
  }

  const accountReady = accountNumber.trim().length >= 10;

  return (
    <div className="max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Beneficiaries</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Bank accounts you can pay out to. Each is verified with the bank before it&apos;s saved, so payouts are fast
          and can&apos;t go to the wrong account.
        </p>
      </div>

      {error && (
        <div className="rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-2.5 text-sm text-destructive">
          {error}
        </div>
      )}

      <div className="rounded-xl border border-border bg-card p-4">
        <b className="text-sm">Add a beneficiary</b>
        <div className="mt-3 space-y-3">
          <input
            className={field}
            placeholder="Account number"
            inputMode="numeric"
            value={accountNumber}
            onChange={(e) => onAccountChange(e.target.value)}
          />

          <div className="relative">
            <input
              className={field}
              placeholder={
                !accountReady
                  ? "Enter the account number first"
                  : loadingBanks
                    ? "Loading banks…"
                    : "Search for the bank…"
              }
              value={bankSearch}
              disabled={!accountReady || loadingBanks || banks.length === 0}
              onChange={(e) => {
                setBankSearch(e.target.value);
                setShowBankList(true);
                setSelectedBank(null);
                setVerifiedName(null);
              }}
              onFocus={() => banks.length > 0 && setShowBankList(true)}
              onBlur={() => setTimeout(() => setShowBankList(false), 150)}
            />
            {showBankList && filteredBanks.length > 0 && (
              <ul className="absolute z-10 mt-1 max-h-56 w-full overflow-auto rounded-md border border-border bg-card shadow-lg">
                {filteredBanks.map((b) => (
                  <li key={b.code}>
                    <button
                      type="button"
                      onClick={() => selectBank(b)}
                      className="block w-full px-3 py-2 text-left text-sm transition hover:bg-accent"
                    >
                      {b.name}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {verifying && <p className="text-xs text-muted-foreground">Verifying account…</p>}
          {verifiedName && (
            <div className="rounded-md border border-green-500/40 bg-green-500/5 px-3 py-2 text-sm">
              <span className="text-muted-foreground">Account name</span>{" "}
              <span className="font-semibold">{verifiedName}</span>
            </div>
          )}
        </div>

        <button
          type="button"
          onClick={create}
          disabled={creating || verifying || !verifiedName}
          className="mt-3 rounded-md bg-primary px-3 py-2 text-sm font-semibold text-primary-foreground transition hover:brightness-105 disabled:opacity-60"
        >
          {creating ? "Adding…" : "+ Add beneficiary"}
        </button>
      </div>

      <div className="rounded-xl border border-border bg-card">
        <div className="border-b border-border px-4 py-3 text-sm font-semibold">Your beneficiaries</div>
        {loading ? (
          <p className="px-4 py-6 text-sm text-muted-foreground">Loading…</p>
        ) : items.length === 0 ? (
          <p className="px-4 py-6 text-sm text-muted-foreground">No beneficiaries yet. Add one above.</p>
        ) : (
          <div className="divide-y divide-border">
            {items.map((b) => (
              <div key={b.id} className="px-4 py-3">
                <div className="flex items-center gap-2">
                  <b className="text-sm">{b.accountName}</b>
                  <span className="text-xs text-muted-foreground">{b.bankName}</span>
                </div>
                <p className="mt-0.5 font-mono text-xs text-muted-foreground">{b.accountNumber}</p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
