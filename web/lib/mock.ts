/**
 * Mock data for the ops dashboard.
 * TODO(backend): replace with live endpoints — customer list, per-customer
 * statement, transactions query, and reconciliation report do not exist yet.
 * Amounts are integer kobo (minor units); render with naira() from lib/utils.
 */

export type StatementRow = { date: string; payer: string; ref: string; match: string; amountKobo: number };

export type Customer = {
  id: string;
  name: string;
  externalId: string;
  accountNumber: string;
  tier: "LEVEL_1" | "LEVEL_2";
  status: "ACTIVE" | "SUSPENDED" | "CLOSED";
  email: string;
  createdAt: string;
  lifetimeKobo: number;
  statement: StatementRow[];
};

export const CUSTOMERS: Customer[] = [
  {
    id: "cus_01HZY8", name: "Amara Okafor", externalId: "user_123", accountNumber: "0123456789",
    tier: "LEVEL_2", status: "ACTIVE", email: "amara@acme.ng", createdAt: "12 Jun 2026", lifetimeKobo: 41850000,
    statement: [
      { date: "02 Jul, 14:20", payer: "John Bello", ref: "nmb_88a1", match: "MATCHED", amountKobo: 5000000 },
      { date: "28 Jun, 09:02", payer: "Acme Payroll", ref: "nmb_871f", match: "MATCHED", amountKobo: 12000000 },
      { date: "19 Jun, 17:44", payer: "Zainab Musa", ref: "nmb_84c0", match: "MATCHED", amountKobo: 1850000 },
    ],
  },
  {
    id: "cus_01HZY9", name: "John Bello", externalId: "user_204", accountNumber: "0123457781",
    tier: "LEVEL_1", status: "ACTIVE", email: "john@acme.ng", createdAt: "18 Jun 2026", lifetimeKobo: 5200000,
    statement: [
      { date: "02 Jul, 13:58", payer: "MTN VTU", ref: "nmb_889c", match: "MATCHED", amountKobo: 200000 },
      { date: "24 Jun, 10:10", payer: "Amara Okafor", ref: "nmb_8620", match: "MATCHED", amountKobo: 5000000 },
    ],
  },
  {
    id: "cus_01HZYA", name: "Zainab Musa", externalId: "user_318", accountNumber: "0123460044",
    tier: "LEVEL_2", status: "SUSPENDED", email: "zainab@acme.ng", createdAt: "20 Jun 2026", lifetimeKobo: 112000000,
    statement: [{ date: "01 Jul, 22:07", payer: "Kunle A.", ref: "nmb_8840", match: "PARTIAL", amountKobo: 990000 }],
  },
  {
    id: "cus_01HZYB", name: "Chidi Eze", externalId: "user_402", accountNumber: "0123461190",
    tier: "LEVEL_1", status: "CLOSED", email: "chidi@acme.ng", createdAt: "24 Jun 2026", lifetimeKobo: 850000,
    statement: [],
  },
];

export type Transaction = {
  date: string; customer: string | null; payer: string; ref: string; match: string; amountKobo: number;
};

export const TRANSACTIONS: Transaction[] = [
  { date: "02 Jul, 14:20", customer: "Amara Okafor", payer: "John Bello", ref: "nmb_88a1", match: "MATCHED", amountKobo: 5000000 },
  { date: "02 Jul, 13:58", customer: "John Bello", payer: "MTN VTU", ref: "nmb_889c", match: "MATCHED", amountKobo: 200000 },
  { date: "02 Jul, 11:40", customer: null, payer: "Unknown", ref: "nmb_8871", match: "ORPHANED", amountKobo: 1500000 },
  { date: "01 Jul, 22:07", customer: "Zainab Musa", payer: "Kunle A.", ref: "nmb_8840", match: "PARTIAL", amountKobo: 990000 },
  { date: "01 Jul, 18:31", customer: "Amara Okafor", payer: "Acme Payroll", ref: "nmb_8815", match: "MATCHED", amountKobo: 12000000 },
];

export type Exception = {
  type: "ORPHANED" | "PARTIAL" | "MISSING"; detail: string; payer: string; ref: string; amountKobo: number; action: string;
};

export const EXCEPTIONS: Exception[] = [
  { type: "ORPHANED", detail: "Credit to unknown account 993…044", payer: "Unknown", ref: "nmb_8871", amountKobo: 1500000, action: "Re-attribute" },
  { type: "ORPHANED", detail: "Transfer with no VA alias", payer: "POS terminal", ref: "nmb_8802", amountKobo: 450000, action: "Dismiss" },
  { type: "PARTIAL", detail: "Provider ₦9,900 vs internal ₦10,000", payer: "Kunle A.", ref: "nmb_8840", amountKobo: 990000, action: "Investigate" },
  { type: "MISSING", detail: "On provider, no webhook — recovered via requery", payer: "Ada N.", ref: "nmb_87f0", amountKobo: 3000000, action: "Ingest" },
  { type: "PARTIAL", detail: "Duplicate provider id — deduped", payer: "John Bello", ref: "nmb_88a1", amountKobo: 5000000, action: "Confirm" },
];

/**
 * Overview figures the stats endpoint does not yet return (mock).
 * recon counts are derived from EXCEPTIONS so every page that summarizes
 * reconciliation (Overview health bar, Reconciliation tiles, sidebar badge)
 * agrees with the one underlying list instead of carrying its own copy.
 */
const MATCHED_COUNT = 127;
const RECON_COUNTS = {
  matched: MATCHED_COUNT,
  partial: EXCEPTIONS.filter((e) => e.type === "PARTIAL").length,
  orphaned: EXCEPTIONS.filter((e) => e.type === "ORPHANED").length,
  missing: EXCEPTIONS.filter((e) => e.type === "MISSING").length,
};

export const OVERVIEW = {
  inflowToday: "₦4.19M",
  inflowDelta: "+8.2%",
  reconciliationRate: "99.4%",
  recon: { ...RECON_COUNTS, total: MATCHED_COUNT + EXCEPTIONS.length },
};
