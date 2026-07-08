/**
 * Mock data for the ops dashboard.
 * TODO(backend): replace with a real customer list endpoint — the illustrative table on the
 * Customers page is the only thing left using this; everything else that used to be mocked
 * (transactions, reconciliation/exceptions, the Overview health bar and inflow chart, the sidebar
 * badge) is now wired to real data.
 * Amounts are integer kobo (minor units); render with naira() from lib/utils.
 */

export type StatementRow = { date: string; payer: string; ref: string; match: string; amountKobo: number };

export type Customer = {
  id: string;
  name: string;
  externalId: string;
  accountNumber: string;
  tier: "TIER_1" | "TIER_2" | "TIER_3";
  status: "ACTIVE" | "SUSPENDED" | "CLOSED";
  email: string;
  createdAt: string;
  lifetimeKobo: number;
  statement: StatementRow[];
};

export const CUSTOMERS: Customer[] = [
  {
    id: "cus_01HZY8", name: "Amara Okafor", externalId: "user_123", accountNumber: "0123456789",
    tier: "TIER_2", status: "ACTIVE", email: "amara@acme.ng", createdAt: "12 Jun 2026", lifetimeKobo: 41850000,
    statement: [
      { date: "02 Jul, 14:20", payer: "John Bello", ref: "nmb_88a1", match: "MATCHED", amountKobo: 5000000 },
      { date: "28 Jun, 09:02", payer: "Acme Payroll", ref: "nmb_871f", match: "MATCHED", amountKobo: 12000000 },
      { date: "19 Jun, 17:44", payer: "Zainab Musa", ref: "nmb_84c0", match: "MATCHED", amountKobo: 1850000 },
    ],
  },
  {
    id: "cus_01HZY9", name: "John Bello", externalId: "user_204", accountNumber: "0123457781",
    tier: "TIER_1", status: "ACTIVE", email: "john@acme.ng", createdAt: "18 Jun 2026", lifetimeKobo: 5200000,
    statement: [
      { date: "02 Jul, 13:58", payer: "MTN VTU", ref: "nmb_889c", match: "MATCHED", amountKobo: 200000 },
      { date: "24 Jun, 10:10", payer: "Amara Okafor", ref: "nmb_8620", match: "MATCHED", amountKobo: 5000000 },
    ],
  },
  {
    id: "cus_01HZYA", name: "Zainab Musa", externalId: "user_318", accountNumber: "0123460044",
    tier: "TIER_2", status: "SUSPENDED", email: "zainab@acme.ng", createdAt: "20 Jun 2026", lifetimeKobo: 112000000,
    statement: [{ date: "01 Jul, 22:07", payer: "Kunle A.", ref: "nmb_8840", match: "PARTIAL", amountKobo: 990000 }],
  },
  {
    id: "cus_01HZYB", name: "Chidi Eze", externalId: "user_402", accountNumber: "0123461190",
    tier: "TIER_1", status: "CLOSED", email: "chidi@acme.ng", createdAt: "24 Jun 2026", lifetimeKobo: 850000,
    statement: [],
  },
];
