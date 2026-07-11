"use client";

import { useCallback, useEffect, useState } from "react";
import { beneficiaryApi, payoutApi, type BankItem, type BeneficiaryItem, type PayoutPage } from "@/lib/api";
import { useDashboardStats } from "@/components/dashboard/stats-context";
import { naira, statusClass } from "@/lib/utils";
import { IconWallet } from "@/components/icons";

const field =
  "w-full rounded-lg border border-border bg-muted px-3 py-2 text-sm outline-none focus:border-primary";

const PAGE_SIZE = 20;

export default function PayoutsPage() {
  const { stats, refreshStats } = useDashboardStats();
  const [payoutPage, setPayoutPage] = useState<PayoutPage | null>(null);
  const [page, setPage] = useState(0);
  const [beneficiaries, setBeneficiaries] = useState<BeneficiaryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Payout form state
  const [creating, setCreating] = useState(false);
  const [beneficiaryId, setBeneficiaryId] = useState("");
  const [amountNaira, setAmountNaira] = useState("");
  const [narration, setNarration] = useState("");

  // Add-a-beneficiary state
  const [banks, setBanks] = useState<BankItem[]>([]);
  const [loadingBanks, setLoadingBanks] = useState(false);
  const [accountNumber, setAccountNumber] = useState("");
  const [bankSearch, setBankSearch] = useState("");
  const [showBankList, setShowBankList] = useState(false);
  const [selectedBank, setSelectedBank] = useState<BankItem | null>(null);
  const [verifiedName, setVerifiedName] = useState<string | null>(null);
  const [verifying, setVerifying] = useState(false);
  const [creatingBeneficiary, setCreatingBeneficiary] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [payoutRes, beneficiaryRes] = await Promise.all([payoutApi.list(page, PAGE_SIZE), beneficiaryApi.list()]);
      setPayoutPage(payoutRes.data);
      setBeneficiaries(beneficiaryRes.data ?? []);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load payouts");
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    Promise.resolve().then(load);
  }, [load]);

  // Fetch bank list once account number reaches 10 digits
  useEffect(() => {
    if (accountNumber.trim().length < 10 || banks.length > 0) return;
    Promise.resolve().then(() => setLoadingBanks(true));
    beneficiaryApi
      .listBanks()
      .then((res) => setBanks(res.data ?? []))
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load bank list"))
      .finally(() => setLoadingBanks(false));
  }, [accountNumber, banks.length]);

  // Verify account when bank is selected against a full account number
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
    setSelectedBank(null);
    setVerifiedName(null);
    setBankSearch("");
    setError(null);
  }

  function selectBank(bank: BankItem) {
    setSelectedBank(bank);
    setBankSearch(bank.name);
    setShowBankList(false);
    setVerifiedName(null);
    setError(null);
  }

  async function addBeneficiary() {
    if (!selectedBank || !verifiedName) return;
    setCreatingBeneficiary(true);
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
      setCreatingBeneficiary(false);
    }
  }

  async function createPayout() {
    const amount = Number(amountNaira);
    if (!beneficiaryId) {
      setError("Choose a beneficiary.");
      return;
    }
    if (!Number.isFinite(amount) || amount <= 0) {
      setError("Enter a valid amount.");
      return;
    }
    setCreating(true);
    setError(null);
    try {
      await payoutApi.create({
        beneficiaryId,
        amount: Math.round(amount * 100),
        narration: narration.trim() || undefined,
      });
      setAmountNaira("");
      setNarration("");
      await Promise.all([load(), refreshStats()]);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Payout failed");
    } finally {
      setCreating(false);
    }
  }

  const accountReady = accountNumber.trim().length >= 10;

  return (
    <div className="max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Payouts</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Withdraw your wallet balance to a bank account.
        </p>
      </div>

      {error && (
        <div className="rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-2.5 text-sm text-destructive">
          {error}
        </div>
      )}

      {/* Balance + Add beneficiary side by side */}
      <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
        <div className="rounded-xl border border-border bg-card p-6">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <IconWallet className="size-4" />
            Available balance
          </div>
          <div className="mt-2 text-4xl font-bold tabular-nums">
            {stats ? naira(stats.walletBalance) : "—"}
          </div>
        </div>

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
            onClick={addBeneficiary}
            disabled={creatingBeneficiary || verifying || !verifiedName}
            className="mt-3 rounded-md bg-primary px-3 py-2 text-sm font-semibold text-primary-foreground transition hover:brightness-105 disabled:opacity-60"
          >
            {creatingBeneficiary ? "Adding…" : "+ Add beneficiary"}
          </button>
        </div>
      </div>

      {/* Send a payout */}
      <div className="rounded-xl border border-border bg-card p-4">
        <b className="text-sm">Send a payout</b>
        {beneficiaries.length === 0 && !loading ? (
          <p className="mt-2 text-sm text-muted-foreground">
            No beneficiaries yet — add one above first.
          </p>
        ) : (
          <div className="mt-3 space-y-3">
            <select className={field} value={beneficiaryId} onChange={(e) => setBeneficiaryId(e.target.value)}>
              <option value="">Choose a beneficiary…</option>
              {beneficiaries.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.accountName} · {b.accountNumber}
                </option>
              ))}
            </select>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <input
                  className={field}
                  placeholder="Amount (₦)"
                  inputMode="decimal"
                  value={amountNaira}
                  onChange={(e) => setAmountNaira(e.target.value)}
                />
                <p className="mt-1 text-xs text-muted-foreground">₦30 flat fee per payout</p>
              </div>
              <input
                className={field}
                placeholder="Narration (optional)"
                value={narration}
                onChange={(e) => setNarration(e.target.value)}
              />
            </div>
            <button
              type="button"
              onClick={createPayout}
              disabled={creating}
              className="rounded-md bg-primary px-3 py-2 text-sm font-semibold text-primary-foreground transition hover:brightness-105 disabled:opacity-60"
            >
              {creating ? "Sending…" : "Send payout"}
            </button>
          </div>
        )}
      </div>

      {/* Beneficiaries list */}
      {beneficiaries.length > 0 && (
        <div className="rounded-xl border border-border bg-card">
          <div className="border-b border-border px-4 py-3 text-sm font-semibold">Your beneficiaries</div>
          <div className="divide-y divide-border">
            {beneficiaries.map((b) => (
              <div key={b.id} className="px-4 py-3">
                <div className="flex items-center gap-2">
                  <b className="text-sm">{b.accountName}</b>
                  <span className="text-xs text-muted-foreground">{b.bankName}</span>
                </div>
                <p className="mt-0.5 font-mono text-xs text-muted-foreground">{b.accountNumber}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Payout history */}
      <div className="rounded-xl border border-border bg-card">
        <div className="border-b border-border px-4 py-3 text-sm font-semibold">Payout history</div>
        {loading ? (
          <p className="px-4 py-6 text-sm text-muted-foreground">Loading…</p>
        ) : !payoutPage || payoutPage.content.length === 0 ? (
          <p className="px-4 py-6 text-sm text-muted-foreground">No payouts yet.</p>
        ) : (
          <div className="divide-y divide-border">
            {payoutPage.content.map((p) => (
              <div key={p.id} className="flex items-center justify-between gap-4 px-4 py-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <code className="truncate font-mono text-sm">{p.reference}</code>
                    <span className={`db dot ${statusClass(p.status)}`}>{p.status}</span>
                  </div>
                  {p.failureReason && <p className="mt-0.5 text-xs text-destructive">{p.failureReason}</p>}
                  <p className="mt-0.5 text-xs text-muted-foreground">{new Date(p.createdAt).toLocaleString()}</p>
                </div>
                <div className="shrink-0 text-right font-mono text-sm tabular-nums">{naira(p.amount)}</div>
              </div>
            ))}
          </div>
        )}
      </div>

      {payoutPage && payoutPage.totalPages > 1 && (
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <span>
            Page {payoutPage.number + 1} of {payoutPage.totalPages} · {payoutPage.totalElements} total
          </span>
          <div className="flex gap-2">
            <button
              type="button"
              disabled={payoutPage.first}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              className="rounded-md border border-border px-2.5 py-1 font-medium transition hover:bg-accent disabled:cursor-not-allowed disabled:opacity-40"
            >
              Previous
            </button>
            <button
              type="button"
              disabled={payoutPage.last}
              onClick={() => setPage((p) => p + 1)}
              className="rounded-md border border-border px-2.5 py-1 font-medium transition hover:bg-accent disabled:cursor-not-allowed disabled:opacity-40"
            >
              Next
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
