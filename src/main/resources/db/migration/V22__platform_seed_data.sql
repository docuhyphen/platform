-- V22 — Platform seed data: APP-scoped Document Library entries and Blueprint
-- definitions covering legal, financial, due diligence, HR, real estate,
-- compliance, and accounting use cases.
--
-- ROLLBACK — run these two statements in order to remove all APP-scoped platform
-- data inserted by this migration:
--
--   DELETE FROM blueprint_definition
--       WHERE scope = 'APP' AND organization_id IS NULL AND created_by_app_user_id IS NULL;
--   (blueprint_document_default rows cascade automatically via ON DELETE CASCADE)
--
--   DELETE FROM document_library
--       WHERE scope = 'APP' AND organization_id IS NULL AND created_by_app_user_id IS NULL;
--

BEGIN;

-- ============================================================
-- 1. DOCUMENT LIBRARY
-- ============================================================

-- Document Library: Legal & Compliance (01–08)
INSERT INTO document_library (
    id, title, description,
    document_type, restrict_type, restricted_type, required,
    scope, is_published, is_active, is_deleted,
    organization_id, created_by_app_user_id, source_document_id,
    file_name, storage_path, content_hash, file_size_bytes,
    general_tags, created_at, updated_at
) VALUES
(
    'a1000000-0000-0000-0000-000000000001',
    'Non-Disclosure Agreement (NDA)',
    'A unilateral confidentiality agreement preventing the receiving party from disclosing proprietary information shared during the exchange. Typically signed before substantive discussions begin.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["legal","nda","confidentiality","compliance"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000002',
    'Mutual Non-Disclosure Agreement',
    'A bilateral NDA where both parties agree to keep each other''s information confidential. Used when both sides will be sharing sensitive information with one another.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["legal","nda","mutual","confidentiality"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000003',
    'Confidentiality Agreement',
    'A broader-form confidentiality undertaking often used in ongoing business relationships or vendor engagements where a full standalone NDA is not required.',
    'PDF', true, 'PDF', false,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["legal","confidentiality","vendor","compliance"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000004',
    'Power of Attorney',
    'A legal instrument authorising one party to act on behalf of another in legal, financial, or administrative matters. Required when a representative will execute documents on behalf of a principal.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["legal","authorization","power-of-attorney","representation"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000005',
    'Legal Retainer Agreement',
    'An engagement agreement between a client and a legal services provider establishing the scope, fees, and terms of the legal representation.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["legal","retainer","engagement","services"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000006',
    'Settlement Agreement',
    'A legally binding agreement resolving a dispute between parties without proceeding to formal litigation or adjudication. Documents the agreed-upon terms of resolution.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["legal","settlement","dispute","resolution"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000007',
    'Arbitration Agreement',
    'A contractual clause or standalone agreement committing parties to resolve disputes through arbitration rather than litigation in a court of law.',
    'PDF', true, 'PDF', false,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["legal","arbitration","dispute","adr"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000008',
    'Cease and Desist Letter',
    'A formal demand letter instructing a party to stop a specific activity alleged to be unlawful or in breach of an agreement, before formal legal proceedings are initiated.',
    'PDF', true, 'PDF', false,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["legal","cease-and-desist","enforcement","ip"]', NOW(), NOW()
)
ON CONFLICT (id) DO NOTHING;

-- Document Library: Corporate Transactions (09–14)
INSERT INTO document_library (
    id, title, description,
    document_type, restrict_type, restricted_type, required,
    scope, is_published, is_active, is_deleted,
    organization_id, created_by_app_user_id, source_document_id,
    file_name, storage_path, content_hash, file_size_bytes,
    general_tags, created_at, updated_at
) VALUES
(
    'a1000000-0000-0000-0000-000000000009',
    'Share Purchase Agreement',
    'The definitive agreement governing the sale and purchase of shares in a company. Includes representations, warranties, conditions precedent to completion, and post-completion obligations.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["corporate","m-and-a","shares","acquisition","legal"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000010',
    'Asset Purchase Agreement',
    'An agreement for the sale and transfer of specific business assets rather than shares in a company. Specifies which assets and liabilities are included and excluded from the transaction.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["corporate","m-and-a","assets","acquisition","legal"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000011',
    'Letter of Intent (LOI)',
    'A preliminary document expressing the intent of one party to enter into a transaction with another. Outlines key terms and conditions before a binding definitive agreement is executed.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["corporate","m-and-a","loi","intent","preliminary"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000012',
    'Term Sheet',
    'A non-binding document summarising the key terms and conditions under which an investment or transaction will proceed. Serves as the basis for drafting definitive legal agreements.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["corporate","m-and-a","investment","term-sheet","preliminary"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000013',
    'Shareholder Agreement',
    'An agreement between the shareholders of a company governing their rights, responsibilities, and the management of the company. Supplements and sits alongside the company''s articles of association.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["corporate","shareholders","governance","legal"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000014',
    'Certificate of Incorporation',
    'The official document issued by a regulatory authority confirming the legal formation and registration of a company. Required as proof of legal existence in most corporate transactions.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["corporate","incorporation","registration","compliance"]', NOW(), NOW()
)
ON CONFLICT (id) DO NOTHING;

-- Document Library: Due Diligence (15–21)
INSERT INTO document_library (
    id, title, description,
    document_type, restrict_type, restricted_type, required,
    scope, is_published, is_active, is_deleted,
    organization_id, created_by_app_user_id, source_document_id,
    file_name, storage_path, content_hash, file_size_bytes,
    general_tags, created_at, updated_at
) VALUES
(
    'a1000000-0000-0000-0000-000000000015',
    'Audited Financial Statements',
    'Independently audited annual financial statements including the balance sheet, income statement, and cash flow statement. Required for financial due diligence in most transactions.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["financial","audit","due-diligence","accounting"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000016',
    'Corporate Tax Returns',
    'Certified copies of the company''s filed corporate income tax returns for the most recent financial periods, as submitted to the relevant tax authority.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["financial","tax","due-diligence","compliance"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000017',
    'Capitalisation Table',
    'A detailed schedule showing the equity ownership structure of a company, including all shareholders, share classes, issued options, warrants, and convertible instruments.',
    'XLSX', true, 'XLSX', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["financial","equity","cap-table","due-diligence","corporate"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000018',
    'Intellectual Property Register',
    'A schedule of all registered and unregistered intellectual property owned or licensed by the company, including patents, trademarks, copyrights, domain names, and software licences.',
    'PDF', true, 'PDF', false,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["ip","due-diligence","legal","corporate"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000019',
    'Material Contracts Summary',
    'A schedule or summary of all material agreements to which the company is party, including customer contracts, supplier agreements, leases, and licences above a defined materiality threshold.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["contracts","due-diligence","legal","corporate"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000020',
    'Organisational Chart & Headcount Report',
    'A current organisational chart and headcount breakdown by department or function, including details of key management roles, reporting lines, and open positions.',
    'XLSX', false, NULL, false,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["hr","due-diligence","corporate","organisation"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000021',
    'Litigation & Disputes Summary',
    'A summary of all current and pending litigation, regulatory investigations, arbitrations, and material disputes involving the company, including status and estimated financial exposure.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["legal","litigation","due-diligence","compliance"]', NOW(), NOW()
)
ON CONFLICT (id) DO NOTHING;

-- Document Library: Financial & Banking (22–27)
INSERT INTO document_library (
    id, title, description,
    document_type, restrict_type, restricted_type, required,
    scope, is_published, is_active, is_deleted,
    organization_id, created_by_app_user_id, source_document_id,
    file_name, storage_path, content_hash, file_size_bytes,
    general_tags, created_at, updated_at
) VALUES
(
    'a1000000-0000-0000-0000-000000000022',
    'Loan Application Form',
    'The standard application form for a credit or lending facility, capturing the borrower''s details, loan purpose, requested amount, repayment preferences, and declared financial position.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["banking","lending","loan","finance"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000023',
    'Credit Agreement',
    'The definitive agreement governing the terms and conditions of a credit facility, including interest rates, repayment schedule, financial covenants, events of default, and security arrangements.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["banking","lending","credit","finance","legal"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000024',
    'Personal Financial Statement',
    'A snapshot of an individual''s personal financial position detailing assets, liabilities, income, and expenses. Commonly required for individual loan applications or personal guarantee arrangements.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["banking","personal-finance","loan","guarantee"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000025',
    'Bank Statements (3 Months)',
    'Three consecutive months of official bank statements for the applicant''s primary operating or personal account, used to verify income, cash flow, and overall financial conduct.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["banking","lending","verification","finance"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000026',
    'Collateral Description & Valuation',
    'A description and independent valuation of assets being offered as security for a loan or credit facility, including real property, plant and equipment, or investment portfolios.',
    'PDF', true, 'PDF', false,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["banking","security","collateral","valuation","lending"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000027',
    'Promissory Note',
    'A written promise by the borrower to repay a specified principal sum to the lender under agreed terms and schedule. Serves as a legally enforceable instrument of debt.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["banking","lending","promissory-note","legal","finance"]', NOW(), NOW()
)
ON CONFLICT (id) DO NOTHING;

-- Document Library: Real Estate (28–34)
INSERT INTO document_library (
    id, title, description,
    document_type, restrict_type, restricted_type, required,
    scope, is_published, is_active, is_deleted,
    organization_id, created_by_app_user_id, source_document_id,
    file_name, storage_path, content_hash, file_size_bytes,
    general_tags, created_at, updated_at
) VALUES
(
    'a1000000-0000-0000-0000-000000000028',
    'Property Sale & Purchase Agreement',
    'The definitive contract for the sale and transfer of real property, setting out the agreed price, conditions of sale, settlement date, and obligations of both the buyer and seller.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["real-estate","property","sale","legal","conveyancing"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000029',
    'Title Search Report',
    'An official report confirming the legal ownership of a property and disclosing any encumbrances, liens, easements, caveats, or covenants registered against the title.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["real-estate","title","property","conveyancing","legal"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000030',
    'Property Valuation / Appraisal Report',
    'An independent professional assessment of the market value of a property, prepared by a certified valuer or appraiser for purposes of lending, sale, insurance, or tax assessment.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["real-estate","valuation","appraisal","property","banking"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000031',
    'Mortgage Application Form',
    'The formal application for a residential or commercial mortgage, capturing property details, borrower information, requested loan amount, and supporting financial disclosures.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["real-estate","mortgage","banking","lending","application"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000032',
    'Survey Report',
    'A professional land survey documenting the precise boundaries, dimensions, and features of a property, including any identified encroachments, rights of way, or easements.',
    'PDF', true, 'PDF', false,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["real-estate","survey","property","conveyancing"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000033',
    'Lease Agreement',
    'A binding contract between a landlord and tenant setting out the terms of occupancy, including rent amount, lease duration, permitted use of the premises, and obligations of each party.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["real-estate","lease","tenancy","property","legal"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000034',
    'Property Disclosure Statement',
    'A seller-completed statement disclosing known material defects, issues, or encumbrances affecting the property. Required in many jurisdictions before exchange of contracts.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["real-estate","disclosure","property","legal","conveyancing"]', NOW(), NOW()
)
ON CONFLICT (id) DO NOTHING;

-- Document Library: HR & Employment (35–40)
INSERT INTO document_library (
    id, title, description,
    document_type, restrict_type, restricted_type, required,
    scope, is_published, is_active, is_deleted,
    organization_id, created_by_app_user_id, source_document_id,
    file_name, storage_path, content_hash, file_size_bytes,
    general_tags, created_at, updated_at
) VALUES
(
    'a1000000-0000-0000-0000-000000000035',
    'Employment Contract',
    'The definitive agreement governing the terms and conditions of employment, including role title, remuneration, working hours, benefits, leave entitlements, and termination provisions.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["hr","employment","contract","onboarding","legal"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000036',
    'Offer Letter',
    'A formal written offer of employment outlining the key terms of the position before the full employment contract is issued. Returned signed by the candidate as acceptance.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["hr","employment","offer","onboarding","recruitment"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000037',
    'Background Check Consent Form',
    'A written authorisation from the prospective employee permitting the employer to conduct background, reference, and credit checks as part of the pre-employment screening process.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["hr","onboarding","compliance","background-check","consent"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000038',
    'Employee Tax Declaration Form',
    'A tax authority-prescribed form completed by a new employee declaring their tax residency status, tax file number, and applicable withholding preferences for payroll purposes.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["hr","tax","onboarding","payroll","compliance"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000039',
    'Direct Deposit Authorisation Form',
    'An authorisation form permitting the employer to make payroll payments directly into the employee''s nominated bank account via electronic funds transfer.',
    'PDF', true, 'PDF', false,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["hr","payroll","banking","onboarding"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000040',
    'Non-Compete & Non-Solicitation Agreement',
    'A contractual undertaking by the employee restricting their ability to work for competitors or solicit clients and colleagues for a defined period after leaving the organisation.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["hr","employment","legal","non-compete","onboarding"]', NOW(), NOW()
)
ON CONFLICT (id) DO NOTHING;

-- Document Library: Compliance & Regulatory (41–46)
INSERT INTO document_library (
    id, title, description,
    document_type, restrict_type, restricted_type, required,
    scope, is_published, is_active, is_deleted,
    organization_id, created_by_app_user_id, source_document_id,
    file_name, storage_path, content_hash, file_size_bytes,
    general_tags, created_at, updated_at
) VALUES
(
    'a1000000-0000-0000-0000-000000000041',
    'AML / KYC Questionnaire',
    'An Anti-Money Laundering and Know Your Customer questionnaire capturing details about the client''s identity, business activities, source of funds, and ultimate beneficial owners.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["compliance","aml","kyc","onboarding","regulatory"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000042',
    'KYC Identity Document',
    'Certified copies of government-issued identification (passport, national ID, or driver''s licence) required to verify the identity of a client or beneficial owner during onboarding.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["compliance","kyc","identity","verification","onboarding"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000043',
    'Regulatory Filing Submission',
    'A formal submission to a regulatory authority, including licence applications, periodic statutory returns, notifications, or formal responses to regulatory enquiries.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["regulatory","compliance","filing","government"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000044',
    'Compliance Certificate',
    'A certificate issued by the company or its legal counsel confirming compliance with applicable laws, regulations, or contractual obligations as at the date specified.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["compliance","certification","legal","regulatory"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000045',
    'Beneficial Ownership Declaration',
    'A declaration identifying the ultimate beneficial owners (UBOs) of a company or structure, including all individuals holding a significant ownership interest or effective control.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["compliance","ubo","beneficial-owner","aml","regulatory"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000046',
    'Data Processing Agreement (DPA)',
    'A contract governing the processing of personal data between a data controller and a data processor, as required under GDPR and equivalent privacy legislation.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["compliance","gdpr","privacy","data-protection","legal"]', NOW(), NOW()
)
ON CONFLICT (id) DO NOTHING;

-- Document Library: Accounting & Audit (47–50)
INSERT INTO document_library (
    id, title, description,
    document_type, restrict_type, restricted_type, required,
    scope, is_published, is_active, is_deleted,
    organization_id, created_by_app_user_id, source_document_id,
    file_name, storage_path, content_hash, file_size_bytes,
    general_tags, created_at, updated_at
) VALUES
(
    'a1000000-0000-0000-0000-000000000047',
    'Audit Engagement Letter',
    'A formal letter confirming the scope, terms, and conditions of an external audit engagement, signed by both the external auditor and the management of the client organisation.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["accounting","audit","engagement","legal"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000048',
    'Management Representation Letter',
    'A letter from company management to the external auditor confirming the accuracy, completeness, and fair presentation of information provided during the audit, as required by auditing standards.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["accounting","audit","management","representation"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000049',
    'Independent Auditor''s Report',
    'The formal opinion issued by the external auditor on completion of an audit, expressing their view on whether the financial statements give a true and fair view of the entity''s financial position.',
    'PDF', true, 'PDF', true,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["accounting","audit","report","financial"]', NOW(), NOW()
),
(
    'a1000000-0000-0000-0000-000000000050',
    'Financial Projections (3-Year Model)',
    'A forward-looking financial model covering the next three fiscal years, including projected revenue, EBITDA, cash flow, and key assumptions. Typically provided in spreadsheet format.',
    'XLSX', true, 'XLSX', false,
    'APP', true, true, false,
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL,
    '["financial","projections","forecast","due-diligence","accounting"]', NOW(), NOW()
)
ON CONFLICT (id) DO NOTHING;


-- ============================================================
-- 2. BLUEPRINT DEFINITIONS
-- ============================================================

-- Blueprint: Standard NDA Exchange
INSERT INTO blueprint_definition (
    id, name, summary, description,
    scope, is_published, is_active, is_deleted, is_template,
    organization_id, created_by_app_user_id, source_template_id,
    config_json, general_tags, created_at, updated_at
) VALUES
(
    'b1000000-0000-0000-0000-000000000001',
    'Standard NDA Exchange',
    'Send a one-way NDA to a counterparty for review and signature.',
    'Use this blueprint to open a simple, structured exchange when you need a third party to sign a one-way non-disclosure agreement before discussions begin. Pre-configured to allow the counterparty to download and return the signed NDA only.',
    'APP', true, true, false, true,
    NULL, NULL, NULL,
    $${"name":"Standard NDA Exchange","description":"Please review and return the signed NDA before we proceed with further discussions.","initialShareMessage":"Please review and sign the attached Non-Disclosure Agreement and return it at your earliest convenience.","requestRecipientSignIn":true,"allowDocumentAddition":false,"allowDocumentDeletion":false,"allowDocumentDownload":true,"allowDocumentUpdate":false,"allowDocumentUpload":true,"allowedDownloadFormats":["PDF"],"recipientConfiguration":{"recipientRoleName":"Counterparty"}}$$,
    '["legal","nda","confidentiality"]',
    NOW(), NOW()
)
ON CONFLICT (id) DO NOTHING;

-- Blueprint: Mutual NDA Exchange
INSERT INTO blueprint_definition (
    id, name, summary, description,
    scope, is_published, is_active, is_deleted, is_template,
    organization_id, created_by_app_user_id, source_template_id,
    config_json, general_tags, created_at, updated_at
) VALUES
(
    'b1000000-0000-0000-0000-000000000002',
    'Mutual NDA Exchange',
    'Exchange a mutual NDA where both parties commit to confidentiality.',
    'Use this blueprint when both parties will be sharing sensitive information and require mutual confidentiality obligations. Pre-configured to allow the counterparty to download, review, and return the countersigned agreement.',
    'APP', true, true, false, true,
    NULL, NULL, NULL,
    $${"name":"Mutual NDA Exchange","description":"Both parties agree to keep all shared information confidential. Please countersign and return the Mutual NDA below.","initialShareMessage":"Please review and countersign the attached Mutual NDA at your earliest convenience.","requestRecipientSignIn":true,"allowDocumentAddition":false,"allowDocumentDeletion":false,"allowDocumentDownload":true,"allowDocumentUpdate":false,"allowDocumentUpload":true,"allowedDownloadFormats":["PDF"],"recipientConfiguration":{"recipientRoleName":"Counterparty"}}$$,
    '["legal","nda","mutual","confidentiality"]',
    NOW(), NOW()
)
ON CONFLICT (id) DO NOTHING;

-- Blueprint: Due Diligence Data Room
INSERT INTO blueprint_definition (
    id, name, summary, description,
    scope, is_published, is_active, is_deleted, is_template,
    organization_id, created_by_app_user_id, source_template_id,
    config_json, general_tags, created_at, updated_at
) VALUES
(
    'b1000000-0000-0000-0000-000000000003',
    'Due Diligence Data Room',
    'A secure data room for sharing corporate and financial documents during due diligence.',
    'Use this blueprint to set up a structured due diligence data room. It pre-loads the most commonly requested financial, legal, and operational documents. Configured to allow the acquirer or investor to upload supplementary materials and download all documents.',
    'APP', true, true, false, true,
    NULL, NULL, NULL,
    $${"name":"Due Diligence Data Room","description":"Welcome to the due diligence data room. Please review all documents and upload any additional materials requested by our team.","initialShareMessage":"You have been granted access to the due diligence data room. Please complete your review by the agreed deadline.","requestRecipientSignIn":true,"allowDocumentAddition":true,"allowDocumentDeletion":false,"allowDocumentDownload":true,"allowDocumentUpdate":true,"allowDocumentUpload":true,"allowedDownloadFormats":["PDF","XLSX"],"recipientConfiguration":{"recipientRoleName":"Acquirer / Investor"}}$$,
    '["due-diligence","m-and-a","data-room","corporate","financial"]',
    NOW(), NOW()
)
ON CONFLICT (id) DO NOTHING;

-- Blueprint: Employment Onboarding Package
INSERT INTO blueprint_definition (
    id, name, summary, description,
    scope, is_published, is_active, is_deleted, is_template,
    organization_id, created_by_app_user_id, source_template_id,
    config_json, general_tags, created_at, updated_at
) VALUES
(
    'b1000000-0000-0000-0000-000000000004',
    'Employment Onboarding Package',
    'Collect signed onboarding documentation from a new employee.',
    'Use this blueprint to open an onboarding exchange for a new hire. Pre-loads all standard employment documentation including the contract, tax declaration, and background check consent. The employee can download, complete, and upload signed copies.',
    'APP', true, true, false, true,
    NULL, NULL, NULL,
    $${"name":"Employment Onboarding Package","description":"Welcome to the team! Please review, complete, and return all documents in this package before your start date.","initialShareMessage":"Please find your onboarding documents attached. Complete and return all required items as soon as possible.","requestRecipientSignIn":true,"allowDocumentAddition":false,"allowDocumentDeletion":false,"allowDocumentDownload":true,"allowDocumentUpdate":false,"allowDocumentUpload":true,"allowedDownloadFormats":["PDF"],"recipientConfiguration":{"recipientRoleName":"New Employee"}}$$,
    '["hr","employment","onboarding","payroll","compliance"]',
    NOW(), NOW()
)
ON CONFLICT (id) DO NOTHING;

-- Blueprint: Real Estate Transaction Package
INSERT INTO blueprint_definition (
    id, name, summary, description,
    scope, is_published, is_active, is_deleted, is_template,
    organization_id, created_by_app_user_id, source_template_id,
    config_json, general_tags, created_at, updated_at
) VALUES
(
    'b1000000-0000-0000-0000-000000000005',
    'Real Estate Transaction Package',
    'Manage the key documents in a property sale or purchase transaction.',
    'Use this blueprint for a property sale or purchase. Pre-loads the sale agreement, title search report, appraisal, and disclosure statement. Designed for a conveyancer or agent to share transaction documents with a buyer or seller for review and signature.',
    'APP', true, true, false, true,
    NULL, NULL, NULL,
    $${"name":"Real Estate Transaction Package","description":"All key transaction documents for your property sale or purchase are available below. Please review and sign where indicated.","initialShareMessage":"Please find your property transaction documents below. Contact us if you have any questions before proceeding.","requestRecipientSignIn":true,"allowDocumentAddition":false,"allowDocumentDeletion":false,"allowDocumentDownload":true,"allowDocumentUpdate":false,"allowDocumentUpload":false,"allowedDownloadFormats":["PDF"],"recipientConfiguration":{"recipientRoleName":"Buyer / Seller"}}$$,
    '["real-estate","property","conveyancing","legal","sale"]',
    NOW(), NOW()
)
ON CONFLICT (id) DO NOTHING;

-- Blueprint: Loan Application Package
INSERT INTO blueprint_definition (
    id, name, summary, description,
    scope, is_published, is_active, is_deleted, is_template,
    organization_id, created_by_app_user_id, source_template_id,
    config_json, general_tags, created_at, updated_at
) VALUES
(
    'b1000000-0000-0000-0000-000000000006',
    'Loan Application Package',
    'Collect all required financial documentation from a loan applicant.',
    'Use this blueprint to gather documents from a loan applicant. Pre-loads the application form, financial statements, and bank statements. Configured to allow the applicant to upload their completed documents directly into the exchange.',
    'APP', true, true, false, true,
    NULL, NULL, NULL,
    $${"name":"Loan Application Package","description":"Please upload all required financial documents to complete your loan application. All submissions will be reviewed by our credit team.","initialShareMessage":"Your loan application document checklist is ready. Please upload all required items to proceed with your application.","requestRecipientSignIn":true,"allowDocumentAddition":false,"allowDocumentDeletion":false,"allowDocumentDownload":true,"allowDocumentUpdate":true,"allowDocumentUpload":true,"allowedDownloadFormats":["PDF"],"recipientConfiguration":{"recipientRoleName":"Applicant"}}$$,
    '["banking","lending","loan","finance","application"]',
    NOW(), NOW()
)
ON CONFLICT (id) DO NOTHING;

-- Blueprint: Corporate Acquisition (M&A) Package
INSERT INTO blueprint_definition (
    id, name, summary, description,
    scope, is_published, is_active, is_deleted, is_template,
    organization_id, created_by_app_user_id, source_template_id,
    config_json, general_tags, created_at, updated_at
) VALUES
(
    'b1000000-0000-0000-0000-000000000007',
    'Corporate Acquisition (M&A) Package',
    'Manage all legal and financial documents across a corporate acquisition or merger.',
    'A comprehensive blueprint for managing documents throughout a corporate acquisition. Covers the full deal lifecycle from LOI through to the definitive SPA. Pre-loads the most commonly required financial, legal, and due diligence documents for both parties.',
    'APP', true, true, false, true,
    NULL, NULL, NULL,
    $${"name":"Corporate Acquisition (M&A) Package","description":"This exchange contains all transaction documents for the proposed acquisition. Please review the current document set and upload any additional materials as requested by the deal team.","initialShareMessage":"Welcome to the deal exchange for this transaction. Please find the current document set below.","requestRecipientSignIn":true,"allowDocumentAddition":true,"allowDocumentDeletion":false,"allowDocumentDownload":true,"allowDocumentUpdate":true,"allowDocumentUpload":true,"allowedDownloadFormats":["PDF","XLSX"],"recipientConfiguration":{"recipientRoleName":"Counterparty"}}$$,
    '["m-and-a","corporate","acquisition","due-diligence","legal","financial"]',
    NOW(), NOW()
)
ON CONFLICT (id) DO NOTHING;

-- Blueprint: Audit Request Package
INSERT INTO blueprint_definition (
    id, name, summary, description,
    scope, is_published, is_active, is_deleted, is_template,
    organization_id, created_by_app_user_id, source_template_id,
    config_json, general_tags, created_at, updated_at
) VALUES
(
    'b1000000-0000-0000-0000-000000000008',
    'Audit Request Package',
    'Manage document requests and submissions for an external or internal audit.',
    'Use this blueprint to issue and track an audit request package. The auditor shares document templates and the auditee uploads completed items. Pre-loads the engagement letter, financial statements, and management representation letter.',
    'APP', true, true, false, true,
    NULL, NULL, NULL,
    $${"name":"Audit Request Package","description":"Please upload all requested audit documents by the agreed deadline. Items marked required must be provided before the audit can be concluded.","initialShareMessage":"Your audit document request list is ready. Please upload all required items by the agreed deadline.","requestRecipientSignIn":true,"allowDocumentAddition":false,"allowDocumentDeletion":false,"allowDocumentDownload":true,"allowDocumentUpdate":true,"allowDocumentUpload":true,"allowedDownloadFormats":["PDF","XLSX"],"recipientConfiguration":{"recipientRoleName":"Auditee"}}$$,
    '["accounting","audit","compliance","financial"]',
    NOW(), NOW()
)
ON CONFLICT (id) DO NOTHING;

-- Blueprint: Vendor Agreement Exchange
INSERT INTO blueprint_definition (
    id, name, summary, description,
    scope, is_published, is_active, is_deleted, is_template,
    organization_id, created_by_app_user_id, source_template_id,
    config_json, general_tags, created_at, updated_at
) VALUES
(
    'b1000000-0000-0000-0000-000000000009',
    'Vendor Agreement Exchange',
    'Onboard a new vendor by executing confidentiality and compliance agreements.',
    'Use this blueprint to open a vendor onboarding exchange. Covers the key agreements required before engaging a new supplier, including a confidentiality undertaking and compliance certification.',
    'APP', true, true, false, true,
    NULL, NULL, NULL,
    $${"name":"Vendor Agreement Exchange","description":"Please review and return all enclosed vendor agreements before your onboarding can be completed.","initialShareMessage":"Welcome. Please sign and return the attached vendor agreements to complete your supplier onboarding.","requestRecipientSignIn":true,"allowDocumentAddition":false,"allowDocumentDeletion":false,"allowDocumentDownload":true,"allowDocumentUpdate":false,"allowDocumentUpload":true,"allowedDownloadFormats":["PDF"],"recipientConfiguration":{"recipientRoleName":"Vendor"}}$$,
    '["vendor","supplier","onboarding","legal","compliance"]',
    NOW(), NOW()
)
ON CONFLICT (id) DO NOTHING;

-- Blueprint: Regulatory Submission Package
INSERT INTO blueprint_definition (
    id, name, summary, description,
    scope, is_published, is_active, is_deleted, is_template,
    organization_id, created_by_app_user_id, source_template_id,
    config_json, general_tags, created_at, updated_at
) VALUES
(
    'b1000000-0000-0000-0000-000000000010',
    'Regulatory Submission Package',
    'Prepare and submit a regulatory filing with all required supporting documents.',
    'Use this blueprint to assemble and deliver a regulatory submission package. Pre-loads the filing form, compliance certificate, and beneficial ownership declaration. Designed for submission to a regulator or government body.',
    'APP', true, true, false, true,
    NULL, NULL, NULL,
    $${"name":"Regulatory Submission Package","description":"This exchange contains your regulatory submission package. Please review all documents and confirm receipt of the submission.","initialShareMessage":"Your regulatory submission package is ready for review. Please acknowledge receipt and confirm all documents have been received.","requestRecipientSignIn":true,"allowDocumentAddition":false,"allowDocumentDeletion":false,"allowDocumentDownload":true,"allowDocumentUpdate":false,"allowDocumentUpload":false,"allowedDownloadFormats":["PDF"],"recipientConfiguration":{"recipientRoleName":"Regulator"}}$$,
    '["regulatory","compliance","filing","government","legal"]',
    NOW(), NOW()
)
ON CONFLICT (id) DO NOTHING;

-- Blueprint: KYC / Client Onboarding
INSERT INTO blueprint_definition (
    id, name, summary, description,
    scope, is_published, is_active, is_deleted, is_template,
    organization_id, created_by_app_user_id, source_template_id,
    config_json, general_tags, created_at, updated_at
) VALUES
(
    'b1000000-0000-0000-0000-000000000011',
    'KYC / Client Onboarding',
    'Collect KYC and AML documentation from a new client as part of compliance onboarding.',
    'A structured onboarding exchange for collecting Know Your Customer and Anti-Money Laundering documentation from a new client. Pre-loads the KYC questionnaire, identity verification documents, and beneficial ownership declaration.',
    'APP', true, true, false, true,
    NULL, NULL, NULL,
    $${"name":"KYC / Client Onboarding","description":"To complete your onboarding, please submit all required compliance documents below. Our team will review your submission within 2 business days.","initialShareMessage":"As part of our client onboarding process, please complete and return the attached compliance documentation at your earliest convenience.","requestRecipientSignIn":true,"allowDocumentAddition":false,"allowDocumentDeletion":false,"allowDocumentDownload":true,"allowDocumentUpdate":true,"allowDocumentUpload":true,"allowedDownloadFormats":["PDF"],"recipientConfiguration":{"recipientRoleName":"Client"}}$$,
    '["kyc","aml","compliance","onboarding","client"]',
    NOW(), NOW()
)
ON CONFLICT (id) DO NOTHING;

-- Blueprint: Lease Agreement Exchange
INSERT INTO blueprint_definition (
    id, name, summary, description,
    scope, is_published, is_active, is_deleted, is_template,
    organization_id, created_by_app_user_id, source_template_id,
    config_json, general_tags, created_at, updated_at
) VALUES
(
    'b1000000-0000-0000-0000-000000000012',
    'Lease Agreement Exchange',
    'Execute a lease agreement and share supporting property documents with a tenant or landlord.',
    'Use this blueprint to open a lease execution exchange. Pre-loads the lease agreement, property valuation, disclosure statement, and survey report. Suitable for both commercial and residential lease transactions.',
    'APP', true, true, false, true,
    NULL, NULL, NULL,
    $${"name":"Lease Agreement Exchange","description":"Please review the lease agreement and all accompanying property documents. Sign and return the agreement where indicated.","initialShareMessage":"Your lease agreement documents are ready for review. Please sign and return the agreement at your earliest convenience.","requestRecipientSignIn":true,"allowDocumentAddition":false,"allowDocumentDeletion":false,"allowDocumentDownload":true,"allowDocumentUpdate":false,"allowDocumentUpload":true,"allowedDownloadFormats":["PDF"],"recipientConfiguration":{"recipientRoleName":"Tenant / Landlord"}}$$,
    '["real-estate","lease","tenancy","property","legal"]',
    NOW(), NOW()
)
ON CONFLICT (id) DO NOTHING;


-- ============================================================
-- 3. BLUEPRINT DOCUMENT DEFAULTS
-- ============================================================

-- Blueprint Document Defaults: Standard NDA Exchange (b...001)
INSERT INTO blueprint_document_default (
    id, blueprint_definition_id,
    title, restricted_type, restrict_type, required,
    library_document_id, display_order
) VALUES
(
    'c1000000-0000-0000-0000-000000000001',
    'b1000000-0000-0000-0000-000000000001',
    'Non-Disclosure Agreement (NDA)', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000001', 0
),
(
    'c1000000-0000-0000-0000-000000000002',
    'b1000000-0000-0000-0000-000000000001',
    'Confidentiality Agreement', 'PDF', true, false,
    'a1000000-0000-0000-0000-000000000003', 1
)
ON CONFLICT (id) DO NOTHING;

-- Blueprint Document Defaults: Mutual NDA Exchange (b...002)
INSERT INTO blueprint_document_default (
    id, blueprint_definition_id,
    title, restricted_type, restrict_type, required,
    library_document_id, display_order
) VALUES
(
    'c1000000-0000-0000-0000-000000000003',
    'b1000000-0000-0000-0000-000000000002',
    'Mutual Non-Disclosure Agreement', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000002', 0
),
(
    'c1000000-0000-0000-0000-000000000004',
    'b1000000-0000-0000-0000-000000000002',
    'Data Processing Agreement (DPA)', 'PDF', true, false,
    'a1000000-0000-0000-0000-000000000046', 1
)
ON CONFLICT (id) DO NOTHING;

-- Blueprint Document Defaults: Due Diligence Data Room (b...003)
INSERT INTO blueprint_document_default (
    id, blueprint_definition_id,
    title, restricted_type, restrict_type, required,
    library_document_id, display_order
) VALUES
(
    'c1000000-0000-0000-0000-000000000005',
    'b1000000-0000-0000-0000-000000000003',
    'Audited Financial Statements', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000015', 0
),
(
    'c1000000-0000-0000-0000-000000000006',
    'b1000000-0000-0000-0000-000000000003',
    'Corporate Tax Returns', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000016', 1
),
(
    'c1000000-0000-0000-0000-000000000007',
    'b1000000-0000-0000-0000-000000000003',
    'Capitalisation Table', 'XLSX', true, true,
    'a1000000-0000-0000-0000-000000000017', 2
),
(
    'c1000000-0000-0000-0000-000000000008',
    'b1000000-0000-0000-0000-000000000003',
    'Intellectual Property Register', 'PDF', true, false,
    'a1000000-0000-0000-0000-000000000018', 3
),
(
    'c1000000-0000-0000-0000-000000000009',
    'b1000000-0000-0000-0000-000000000003',
    'Material Contracts Summary', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000019', 4
),
(
    'c1000000-0000-0000-0000-000000000010',
    'b1000000-0000-0000-0000-000000000003',
    'Organisational Chart & Headcount Report', 'XLSX', true, false,
    'a1000000-0000-0000-0000-000000000020', 5
),
(
    'c1000000-0000-0000-0000-000000000011',
    'b1000000-0000-0000-0000-000000000003',
    'Litigation & Disputes Summary', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000021', 6
)
ON CONFLICT (id) DO NOTHING;

-- Blueprint Document Defaults: Employment Onboarding Package (b...004)
INSERT INTO blueprint_document_default (
    id, blueprint_definition_id,
    title, restricted_type, restrict_type, required,
    library_document_id, display_order
) VALUES
(
    'c1000000-0000-0000-0000-000000000012',
    'b1000000-0000-0000-0000-000000000004',
    'Employment Contract', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000035', 0
),
(
    'c1000000-0000-0000-0000-000000000013',
    'b1000000-0000-0000-0000-000000000004',
    'Offer Letter', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000036', 1
),
(
    'c1000000-0000-0000-0000-000000000014',
    'b1000000-0000-0000-0000-000000000004',
    'Background Check Consent Form', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000037', 2
),
(
    'c1000000-0000-0000-0000-000000000015',
    'b1000000-0000-0000-0000-000000000004',
    'Employee Tax Declaration Form', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000038', 3
),
(
    'c1000000-0000-0000-0000-000000000016',
    'b1000000-0000-0000-0000-000000000004',
    'Direct Deposit Authorisation Form', 'PDF', true, false,
    'a1000000-0000-0000-0000-000000000039', 4
),
(
    'c1000000-0000-0000-0000-000000000017',
    'b1000000-0000-0000-0000-000000000004',
    'Non-Compete & Non-Solicitation Agreement', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000040', 5
)
ON CONFLICT (id) DO NOTHING;

-- Blueprint Document Defaults: Real Estate Transaction Package (b...005)
INSERT INTO blueprint_document_default (
    id, blueprint_definition_id,
    title, restricted_type, restrict_type, required,
    library_document_id, display_order
) VALUES
(
    'c1000000-0000-0000-0000-000000000018',
    'b1000000-0000-0000-0000-000000000005',
    'Property Sale & Purchase Agreement', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000028', 0
),
(
    'c1000000-0000-0000-0000-000000000019',
    'b1000000-0000-0000-0000-000000000005',
    'Title Search Report', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000029', 1
),
(
    'c1000000-0000-0000-0000-000000000020',
    'b1000000-0000-0000-0000-000000000005',
    'Property Valuation / Appraisal Report', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000030', 2
),
(
    'c1000000-0000-0000-0000-000000000021',
    'b1000000-0000-0000-0000-000000000005',
    'Mortgage Application Form', 'PDF', true, false,
    'a1000000-0000-0000-0000-000000000031', 3
),
(
    'c1000000-0000-0000-0000-000000000022',
    'b1000000-0000-0000-0000-000000000005',
    'Survey Report', 'PDF', true, false,
    'a1000000-0000-0000-0000-000000000032', 4
),
(
    'c1000000-0000-0000-0000-000000000023',
    'b1000000-0000-0000-0000-000000000005',
    'Property Disclosure Statement', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000034', 5
)
ON CONFLICT (id) DO NOTHING;

-- Blueprint Document Defaults: Loan Application Package (b...006)
INSERT INTO blueprint_document_default (
    id, blueprint_definition_id,
    title, restricted_type, restrict_type, required,
    library_document_id, display_order
) VALUES
(
    'c1000000-0000-0000-0000-000000000024',
    'b1000000-0000-0000-0000-000000000006',
    'Loan Application Form', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000022', 0
),
(
    'c1000000-0000-0000-0000-000000000025',
    'b1000000-0000-0000-0000-000000000006',
    'Personal Financial Statement', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000024', 1
),
(
    'c1000000-0000-0000-0000-000000000026',
    'b1000000-0000-0000-0000-000000000006',
    'Bank Statements (3 Months)', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000025', 2
),
(
    'c1000000-0000-0000-0000-000000000027',
    'b1000000-0000-0000-0000-000000000006',
    'Collateral Description & Valuation', 'PDF', true, false,
    'a1000000-0000-0000-0000-000000000026', 3
),
(
    'c1000000-0000-0000-0000-000000000028',
    'b1000000-0000-0000-0000-000000000006',
    'Audited Financial Statements', 'PDF', true, false,
    'a1000000-0000-0000-0000-000000000015', 4
)
ON CONFLICT (id) DO NOTHING;

-- Blueprint Document Defaults: Corporate Acquisition (M&A) Package (b...007)
INSERT INTO blueprint_document_default (
    id, blueprint_definition_id,
    title, restricted_type, restrict_type, required,
    library_document_id, display_order
) VALUES
(
    'c1000000-0000-0000-0000-000000000029',
    'b1000000-0000-0000-0000-000000000007',
    'Letter of Intent (LOI)', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000011', 0
),
(
    'c1000000-0000-0000-0000-000000000030',
    'b1000000-0000-0000-0000-000000000007',
    'Term Sheet', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000012', 1
),
(
    'c1000000-0000-0000-0000-000000000031',
    'b1000000-0000-0000-0000-000000000007',
    'Share Purchase Agreement', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000009', 2
),
(
    'c1000000-0000-0000-0000-000000000032',
    'b1000000-0000-0000-0000-000000000007',
    'Audited Financial Statements', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000015', 3
),
(
    'c1000000-0000-0000-0000-000000000033',
    'b1000000-0000-0000-0000-000000000007',
    'Capitalisation Table', 'XLSX', true, true,
    'a1000000-0000-0000-0000-000000000017', 4
),
(
    'c1000000-0000-0000-0000-000000000034',
    'b1000000-0000-0000-0000-000000000007',
    'Material Contracts Summary', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000019', 5
),
(
    'c1000000-0000-0000-0000-000000000035',
    'b1000000-0000-0000-0000-000000000007',
    'Litigation & Disputes Summary', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000021', 6
),
(
    'c1000000-0000-0000-0000-000000000036',
    'b1000000-0000-0000-0000-000000000007',
    'Shareholder Agreement', 'PDF', true, false,
    'a1000000-0000-0000-0000-000000000013', 7
)
ON CONFLICT (id) DO NOTHING;

-- Blueprint Document Defaults: Audit Request Package (b...008)
INSERT INTO blueprint_document_default (
    id, blueprint_definition_id,
    title, restricted_type, restrict_type, required,
    library_document_id, display_order
) VALUES
(
    'c1000000-0000-0000-0000-000000000037',
    'b1000000-0000-0000-0000-000000000008',
    'Audit Engagement Letter', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000047', 0
),
(
    'c1000000-0000-0000-0000-000000000038',
    'b1000000-0000-0000-0000-000000000008',
    'Audited Financial Statements', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000015', 1
),
(
    'c1000000-0000-0000-0000-000000000039',
    'b1000000-0000-0000-0000-000000000008',
    'Corporate Tax Returns', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000016', 2
),
(
    'c1000000-0000-0000-0000-000000000040',
    'b1000000-0000-0000-0000-000000000008',
    'Management Representation Letter', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000048', 3
),
(
    'c1000000-0000-0000-0000-000000000041',
    'b1000000-0000-0000-0000-000000000008',
    'Financial Projections (3-Year Model)', 'XLSX', true, false,
    'a1000000-0000-0000-0000-000000000050', 4
)
ON CONFLICT (id) DO NOTHING;

-- Blueprint Document Defaults: Vendor Agreement Exchange (b...009)
INSERT INTO blueprint_document_default (
    id, blueprint_definition_id,
    title, restricted_type, restrict_type, required,
    library_document_id, display_order
) VALUES
(
    'c1000000-0000-0000-0000-000000000042',
    'b1000000-0000-0000-0000-000000000009',
    'Non-Disclosure Agreement (NDA)', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000001', 0
),
(
    'c1000000-0000-0000-0000-000000000043',
    'b1000000-0000-0000-0000-000000000009',
    'Confidentiality Agreement', 'PDF', true, false,
    'a1000000-0000-0000-0000-000000000003', 1
),
(
    'c1000000-0000-0000-0000-000000000044',
    'b1000000-0000-0000-0000-000000000009',
    'Compliance Certificate', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000044', 2
)
ON CONFLICT (id) DO NOTHING;

-- Blueprint Document Defaults: Regulatory Submission Package (b...010)
INSERT INTO blueprint_document_default (
    id, blueprint_definition_id,
    title, restricted_type, restrict_type, required,
    library_document_id, display_order
) VALUES
(
    'c1000000-0000-0000-0000-000000000045',
    'b1000000-0000-0000-0000-000000000010',
    'Regulatory Filing Submission', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000043', 0
),
(
    'c1000000-0000-0000-0000-000000000046',
    'b1000000-0000-0000-0000-000000000010',
    'Compliance Certificate', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000044', 1
),
(
    'c1000000-0000-0000-0000-000000000047',
    'b1000000-0000-0000-0000-000000000010',
    'Beneficial Ownership Declaration', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000045', 2
),
(
    'c1000000-0000-0000-0000-000000000048',
    'b1000000-0000-0000-0000-000000000010',
    'Data Processing Agreement (DPA)', 'PDF', true, false,
    'a1000000-0000-0000-0000-000000000046', 3
)
ON CONFLICT (id) DO NOTHING;

-- Blueprint Document Defaults: KYC / Client Onboarding (b...011)
INSERT INTO blueprint_document_default (
    id, blueprint_definition_id,
    title, restricted_type, restrict_type, required,
    library_document_id, display_order
) VALUES
(
    'c1000000-0000-0000-0000-000000000049',
    'b1000000-0000-0000-0000-000000000011',
    'AML / KYC Questionnaire', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000041', 0
),
(
    'c1000000-0000-0000-0000-000000000050',
    'b1000000-0000-0000-0000-000000000011',
    'KYC Identity Document', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000042', 1
),
(
    'c1000000-0000-0000-0000-000000000051',
    'b1000000-0000-0000-0000-000000000011',
    'Beneficial Ownership Declaration', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000045', 2
),
(
    'c1000000-0000-0000-0000-000000000052',
    'b1000000-0000-0000-0000-000000000011',
    'Data Processing Agreement (DPA)', 'PDF', true, false,
    'a1000000-0000-0000-0000-000000000046', 3
)
ON CONFLICT (id) DO NOTHING;

-- Blueprint Document Defaults: Lease Agreement Exchange (b...012)
INSERT INTO blueprint_document_default (
    id, blueprint_definition_id,
    title, restricted_type, restrict_type, required,
    library_document_id, display_order
) VALUES
(
    'c1000000-0000-0000-0000-000000000053',
    'b1000000-0000-0000-0000-000000000012',
    'Lease Agreement', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000033', 0
),
(
    'c1000000-0000-0000-0000-000000000054',
    'b1000000-0000-0000-0000-000000000012',
    'Property Valuation / Appraisal Report', 'PDF', true, false,
    'a1000000-0000-0000-0000-000000000030', 1
),
(
    'c1000000-0000-0000-0000-000000000055',
    'b1000000-0000-0000-0000-000000000012',
    'Property Disclosure Statement', 'PDF', true, true,
    'a1000000-0000-0000-0000-000000000034', 2
),
(
    'c1000000-0000-0000-0000-000000000056',
    'b1000000-0000-0000-0000-000000000012',
    'Survey Report', 'PDF', true, false,
    'a1000000-0000-0000-0000-000000000032', 3
)
ON CONFLICT (id) DO NOTHING;

COMMIT;
