/**
 * Starting points for the new-process form.
 *
 * These are *inputs*, not outputs — they fill in the same fields a person would type and are then
 * analysed by exactly the same pipeline as anything else. Nothing about them is pre-computed.
 *
 * They are deliberately spread across unrelated industries, because the most convincing thing a
 * demo can do is run on something the research library knows nothing about. "Student admissions
 * screening" will match the education sources; "warehouse picking" will match nothing and say so
 * with a relevance score of zero — both outcomes are worth showing.
 */

export interface ProcessExample {
  id: string;
  name: string;
  industry: string;
  /** One line shown on the picker button. */
  teaser: string;
  description: string;
  activities: {
    name: string;
    description: string;
    roles: string;
    systems: string;
  }[];
}

export const PROCESS_EXAMPLES: ProcessExample[] = [
  {
    id: "expenses",
    name: "Employee Expense Reimbursement",
    industry: "Corporate Shared Services",
    teaser: "Receipts, policy checks and a manager sign-off",
    description:
      "How an employee claims a travel expense and how finance checks, approves and pays it back.",
    activities: [
      {
        name: "Submit the expense claim",
        description: "The employee fills a form and attaches photographed receipts.",
        roles: "Employee",
        systems: "Expense Portal",
      },
      {
        name: "Check receipts against policy",
        description:
          "A finance associate reads each receipt and checks the amount, date and category against the travel policy.",
        roles: "Finance Associate",
        systems: "Expense Portal, Policy Document",
      },
      {
        name: "Route for manager approval",
        description: "The claim is emailed to the reporting manager for sign-off.",
        roles: "Finance Associate, Reporting Manager",
        systems: "Email",
      },
      {
        name: "Process payment and close the claim",
        description: "Approved claims are batched into the payroll run and the claim is marked paid.",
        roles: "Finance Associate",
        systems: "Payroll System",
      },
    ],
  },
  {
    id: "admissions",
    name: "Student Admissions Screening",
    industry: "Higher Education",
    teaser: "Shortlisting applicants against entry criteria",
    description:
      "How a university admissions office screens applications for an oversubscribed postgraduate course, from receipt to offer decision.",
    activities: [
      {
        name: "Receive and log the application",
        description:
          "Applications arrive through the portal and by email; an officer checks the file is complete and chases anything missing.",
        roles: "Admissions Officer",
        systems: "Admissions Portal, Email",
      },
      {
        name: "Verify transcripts and qualifications",
        description:
          "Certificates are compared against the entry criteria and, for overseas awards, against an equivalency table held in a spreadsheet.",
        roles: "Admissions Officer",
        systems: "Spreadsheet, Document Store",
      },
      {
        name: "Score the personal statement",
        description:
          "Two academics read each statement and score it against a rubric; disagreements go to a third reader.",
        roles: "Academic Reviewer",
        systems: "Review Console",
      },
      {
        name: "Rank the shortlist and decide offers",
        description:
          "The admissions panel meets weekly to rank candidates and allocate the limited places.",
        roles: "Admissions Panel, Programme Director",
        systems: "Spreadsheet",
      },
      {
        name: "Send the decision to the applicant",
        description:
          "Offer and rejection letters are generated from templates and emailed, with conditions typed in by hand.",
        roles: "Admissions Officer",
        systems: "Email, Admissions Portal",
      },
    ],
  },
  {
    id: "discharge",
    name: "Hospital Patient Discharge",
    industry: "Healthcare",
    teaser: "Discharge summaries, medicines and follow-up",
    description:
      "How a ward discharges an inpatient: clinical sign-off, discharge summary, take-home medicines and the follow-up appointment.",
    activities: [
      {
        name: "Confirm the patient is fit for discharge",
        description:
          "The consultant reviews observations and notes on the ward round and marks the patient for discharge.",
        roles: "Consultant, Ward Nurse",
        systems: "Electronic Patient Record",
      },
      {
        name: "Write the discharge summary",
        description:
          "A junior doctor types a summary of the admission, diagnosis and medication changes, often at the end of a shift.",
        roles: "Junior Doctor",
        systems: "Electronic Patient Record",
      },
      {
        name: "Prepare take-home medicines",
        description:
          "Pharmacy reads the summary, dispenses the medicines and counsels the patient. This is usually the longest wait.",
        roles: "Pharmacist",
        systems: "Pharmacy System",
      },
      {
        name: "Book the follow-up appointment",
        description:
          "A ward clerk phones the outpatient department to find a slot and writes it on the discharge letter.",
        roles: "Ward Clerk",
        systems: "Appointment System, Phone",
      },
      {
        name: "Send the summary to the family doctor",
        description:
          "The summary is faxed or emailed to the GP practice, sometimes days after the patient has left.",
        roles: "Ward Clerk",
        systems: "Email",
      },
    ],
  },
  {
    id: "picking",
    name: "Warehouse Order Picking & Dispatch",
    industry: "Logistics & Warehousing",
    teaser: "Paper pick lists, packing and courier handover",
    description:
      "How a distribution centre turns a customer order into a packed parcel on a courier van, from pick list to proof of dispatch.",
    activities: [
      {
        name: "Release the pick list",
        description:
          "A supervisor prints pick lists in order of arrival and hands them to pickers at the start of the shift.",
        roles: "Warehouse Supervisor",
        systems: "Order Management System, Printer",
      },
      {
        name: "Pick items from the racks",
        description:
          "Pickers walk the aisles with a paper list and a trolley, ticking off items as they go.",
        roles: "Picker",
        systems: "Paper Pick List",
      },
      {
        name: "Check and pack the order",
        description:
          "A packer re-counts the items against the order, chooses a box size by eye and packs it.",
        roles: "Packer",
        systems: "Weighing Scale",
      },
      {
        name: "Book the courier and print the label",
        description:
          "The dispatcher picks a courier based on destination and habit, then keys the address into the courier portal.",
        roles: "Dispatcher",
        systems: "Courier Portal",
      },
      {
        name: "Handle shortages and mis-picks",
        description:
          "Missing or wrong items are discovered at packing; the order is set aside and reworked the next day.",
        roles: "Warehouse Supervisor, Packer",
        systems: "Order Management System",
      },
    ],
  },
  {
    id: "complaints",
    name: "Customer Complaint Resolution",
    industry: "Telecommunications",
    teaser: "Multi-channel intake, triage and compensation",
    description:
      "How a telecom operator handles a billing or service complaint from first contact through investigation to resolution and compensation.",
    activities: [
      {
        name: "Log the complaint",
        description:
          "Complaints arrive by phone, app and social media; agents summarise them in free text, so the same issue is described five different ways.",
        roles: "Contact Centre Agent",
        systems: "CRM, Social Media Inbox",
      },
      {
        name: "Classify and prioritise",
        description:
          "A team leader reads the queue each morning and assigns a category, severity and owner.",
        roles: "Team Leader",
        systems: "CRM",
      },
      {
        name: "Investigate the account",
        description:
          "The agent opens the billing system, the network fault log and the order history separately and pieces the story together.",
        roles: "Contact Centre Agent",
        systems: "Billing System, Network Fault Log, CRM",
      },
      {
        name: "Decide compensation",
        description:
          "Goodwill credits are decided case by case against a policy note, so similar complaints get different outcomes.",
        roles: "Team Leader",
        systems: "Billing System",
      },
      {
        name: "Close the case and report",
        description:
          "The agent writes a closing note and the monthly regulatory report is compiled by hand from exported spreadsheets.",
        roles: "Contact Centre Agent, Compliance Analyst",
        systems: "CRM, Spreadsheet",
      },
    ],
  },
  {
    id: "maintenance",
    name: "Tenant Maintenance Requests",
    industry: "Property Management",
    teaser: "Repair reports, contractor dispatch and sign-off",
    description:
      "How a property manager handles a repair reported by a tenant, from the first message to the contractor invoice being paid.",
    activities: [
      {
        name: "Take the repair report",
        description:
          "Tenants report faults by phone or WhatsApp, often with a photo and a vague description of the problem.",
        roles: "Property Manager",
        systems: "Phone, WhatsApp",
      },
      {
        name: "Decide urgency and trade",
        description:
          "The manager judges from the description whether it is an emergency and which trade is needed.",
        roles: "Property Manager",
        systems: "Spreadsheet",
      },
      {
        name: "Find an available contractor",
        description:
          "The manager rings contractors from a personal list until one can attend, then confirms the slot with the tenant.",
        roles: "Property Manager",
        systems: "Phone, Contractor List",
      },
      {
        name: "Verify the work and pay the invoice",
        description:
          "The contractor emails an invoice with a photo; the manager checks it against the quote and forwards it to accounts.",
        roles: "Property Manager, Accounts Clerk",
        systems: "Email, Accounting System",
      },
    ],
  },
];
