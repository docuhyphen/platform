const HARDCODED_OTP = "123456";

const industries = [
  {
    key: "banking-lending",
    name: "Banking / Lending",
    reportTitle: "Credit Risk Assessment",
    contactFirstName: "Daniel",
    contactLastName: "Kessler",
    orgPrefix: "Meridian Trust Lending",
    recipientEmail: "demo.banking@doc-hyphen.com",
    description: "Credit risk assessment and lending review workflow.",
    message: "Please review these lending documents and provide a response.",
    docTitles: [
      "Loan Application Form",
      "Credit Bureau Snapshot",
      "Debt-to-Income Worksheet",
      "Collateral Valuation",
      "Income Verification",
      "Risk Committee Notes",
      "Offer Letter Draft",
      "Affordability Assessment"
    ]
  },
  {
    key: "healthcare-medical",
    name: "Healthcare / Medical Practice",
    reportTitle: "Patient Referral Summary",
    contactFirstName: "Amelia",
    contactLastName: "Nolan",
    orgPrefix: "Cedar Grove Medical",
    recipientEmail: "demo.healthcare@doc-hyphen.com",
    description: "Patient referral and record-sharing review workflow.",
    message: "Please review this referral package and confirm specialist notes.",
    docTitles: [
      "Patient Referral Summary",
      "CBC Lab Results",
      "Imaging Report",
      "Medication History",
      "Clinical Intake Notes",
      "Consent Confirmation",
      "Specialist Request Letter",
      "Follow-Up Plan"
    ]
  },
  {
    key: "accounting-audit",
    name: "Accounting / Audit",
    reportTitle: "Quarterly Financial Report",
    contactFirstName: "Elias",
    contactLastName: "Finch",
    orgPrefix: "Alder Finch Advisory",
    recipientEmail: "demo.accounting@doc-hyphen.com",
    description: "Quarterly financial package and audit evidence workflow.",
    message: "Please review the quarterly financial package and evidence logs.",
    docTitles: [
      "Quarterly Revenue Breakdown",
      "Expense Ledger Extract",
      "P&L Statement",
      "Balance Sheet Snapshot",
      "Cash Flow Summary",
      "Audit Evidence Tracker",
      "Ratio Analysis Sheet",
      "Management Representation Draft"
    ]
  },
  {
    key: "real-estate-property",
    name: "Real Estate / Property Management",
    reportTitle: "Property Investment Report",
    contactFirstName: "Lena",
    contactLastName: "Brooks",
    orgPrefix: "Harbor View Property",
    recipientEmail: "demo.realestate@doc-hyphen.com",
    description: "Property investment and rental performance review workflow.",
    message: "Please review this property investment analysis package.",
    docTitles: [
      "Property Overview Report",
      "Rental Income Projection",
      "Operating Expense Statement",
      "Market Comparison Sheet",
      "Lease Occupancy Summary",
      "Cap Rate Assessment",
      "Building Inspection Notes",
      "Title Deed Copy"
    ]
  },
  {
    key: "legal-law-firm",
    name: "Legal / Law Firm",
    reportTitle: "Case File Summary",
    contactFirstName: "Rowan",
    contactLastName: "Vance",
    orgPrefix: "Halbrook Vance Legal",
    recipientEmail: "demo.legal@doc-hyphen.com",
    description: "Case file summary and evidence review workflow.",
    message: "Please review the case documents and update evidence status.",
    docTitles: [
      "Case Overview Memo",
      "Party Representation Matrix",
      "Event Timeline",
      "Evidence Index",
      "Contract Bundle",
      "Affidavit Collection",
      "Correspondence Log",
      "Court Filing Packet"
    ]
  }
];

const sessionScenariosByIndustry = {
  "banking-lending": [
    {
      sessionName: "Retail Mortgage Affordability Review",
      description: "Underwriting review for mortgage affordability, debt exposure, and collateral readiness."
    },
    {
      sessionName: "Vehicle Finance Credit Evaluation",
      description: "Credit and income verification workflow for secured vehicle finance applicants."
    },
    {
      sessionName: "SME Working Capital Application Assessment",
      description: "Risk review for small business working capital requests including cash flow checks."
    },
    {
      sessionName: "Personal Loan Eligibility Screening",
      description: "Initial lending assessment to validate affordability, identity, and repayment profile."
    }
  ],
  "healthcare-medical": [
    {
      sessionName: "Gastroenterology Referral Intake",
      description: "Referral package review for specialist gastroenterology consultation and care planning."
    },
    {
      sessionName: "Cardiology Follow-Up Coordination",
      description: "Clinical record exchange for cardiology follow-up and ongoing symptom tracking."
    },
    {
      sessionName: "Orthopedic Imaging Review Request",
      description: "Specialist review session for imaging, prior treatment notes, and mobility observations."
    },
    {
      sessionName: "Endocrine Care Referral Package",
      description: "Endocrinology referral preparation with lab history and medication reconciliation."
    }
  ],
  "accounting-audit": [
    {
      sessionName: "Quarterly Revenue Substantiation Review",
      description: "Audit support session to validate revenue posting, source documents, and variances."
    },
    {
      sessionName: "Expense Classification Compliance Check",
      description: "Review of major operating expenses for policy compliance and posting accuracy."
    },
    {
      sessionName: "Management Accounts Closing Pack",
      description: "Month-end close documentation review with reconciliation and sign-off artifacts."
    },
    {
      sessionName: "Inventory and COGS Validation Cycle",
      description: "Testing session for inventory movement records and cost-of-sales consistency."
    }
  ],
  "real-estate-property": [
    {
      sessionName: "Multifamily Acquisition Due Diligence",
      description: "Investment diligence review including leases, occupancy, and property condition records."
    },
    {
      sessionName: "Rental Yield Performance Review",
      description: "Portfolio review of rental income quality, vacancy trends, and tenant retention data."
    },
    {
      sessionName: "Property Operations Cost Benchmark",
      description: "Operating expense and maintenance benchmark review across comparable assets."
    },
    {
      sessionName: "Cap Rate and NOI Verification",
      description: "Financial verification session focused on NOI assumptions and cap-rate sensitivity."
    }
  ],
  "legal-law-firm": [
    {
      sessionName: "Commercial Contract Dispute File Review",
      description: "Case review for contract dispute chronology, correspondence, and evidence completeness."
    },
    {
      sessionName: "Pre-Trial Evidence Preparation",
      description: "Litigation prep session to organize filings, affidavits, and supporting exhibits."
    },
    {
      sessionName: "Discovery Production Coordination",
      description: "Discovery document exchange review with index verification and status reconciliation."
    },
    {
      sessionName: "Settlement Readiness Dossier",
      description: "Counsel review package for settlement position, damages support, and negotiation inputs."
    }
  ]
};

const baseUrlInput = document.getElementById("baseUrl");
const emailDomainInput = document.getElementById("emailDomain");
const passwordInput = document.getElementById("password");
const sessionsInput = document.getElementById("sessionsPerIndustry");
const delayInput = document.getElementById("delayMs");
const generateBtn = document.getElementById("generateBtn");
const clearBtn = document.getElementById("clearBtn");
const statusNode = document.getElementById("status");
const logNode = document.getElementById("log");

function setStatus(message, isError = false) {
  statusNode.textContent = message;
  statusNode.dataset.state = isError ? "error" : "ok";
}

function log(message) {
  const timestamp = new Date().toISOString();
  logNode.textContent += `[${timestamp}] ${message}\n`;
  logNode.scrollTop = logNode.scrollHeight;
}

function sleep(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

function toTwoDigits(value) {
  return String(value).padStart(2, "0");
}

function sanitizeKey(value) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "");
}

function nowRunTag() {
  const d = new Date();
  const yyyy = d.getUTCFullYear();
  const mm = toTwoDigits(d.getUTCMonth() + 1);
  const dd = toTwoDigits(d.getUTCDate());
  const hh = toTwoDigits(d.getUTCHours());
  const mi = toTwoDigits(d.getUTCMinutes());
  const ss = toTwoDigits(d.getUTCSeconds());
  const rand = randomInt(100, 999);
  return `${yyyy}${mm}${dd}${hh}${mi}${ss}${rand}`;
}

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function pickUniqueTitles(titles, count) {
  const copy = [...titles];
  const selected = [];

  while (selected.length < count && copy.length > 0) {
    const idx = randomInt(0, copy.length - 1);
    selected.push(copy[idx]);
    copy.splice(idx, 1);
  }

  return selected;
}

function createSessionDocuments(industry, sessionNumber, sessionsPerIndustry) {
  const docCount = randomInt(3, 6);
  const isThirdLastSession = sessionsPerIndustry >= 3 && sessionNumber === sessionsPerIndustry - 2;
  const forcedFirstTitle = isThirdLastSession ? industry.reportTitle : null;

  const titlePool = forcedFirstTitle
    ? industry.docTitles.filter((title) => title !== forcedFirstTitle)
    : industry.docTitles;

  const randomTitleCount = forcedFirstTitle ? Math.max(2, docCount - 1) : docCount;
  const chosenTitles = pickUniqueTitles(titlePool, randomTitleCount);

  const finalTitles = forcedFirstTitle ? [forcedFirstTitle, ...chosenTitles] : chosenTitles;

  return finalTitles.map((title, index) => ({
    title: index === 0 && forcedFirstTitle ? title : `${title} - File ${index + 1}`,
    restrictType: false
  }));
}

function getSessionScenario(industry, sessionNumber) {
  const scenarios = sessionScenariosByIndustry[industry.key] || [];
  if (scenarios.length === 0) {
    const number = toTwoDigits(sessionNumber);
    return {
      sessionName: `${industry.name} Intake Case ${number}`,
      description: `${industry.description} Case pack ${number} for review and processing.`
    };
  }

  const scenario = scenarios[(sessionNumber - 1) % scenarios.length];
  const caseRef = `${industry.key.toUpperCase().slice(0, 4)}-${toTwoDigits(sessionNumber)}`;
  return {
    sessionName: `${scenario.sessionName} (${caseRef})`,
    description: `${scenario.description} Reference ${caseRef}.`
  };
}

function buildSessionPayload(industry, sessionNumber, sessionsPerIndustry) {
  const scenario = getSessionScenario(industry, sessionNumber);
  return {
    sessionName: scenario.sessionName,
    description: scenario.description,
    recipientEmail: industry.recipientEmail,
    initialShareMessage: industry.message,
    sessionDocuments: createSessionDocuments(industry, sessionNumber, sessionsPerIndustry),
    requestRecipientSignIn: false,
    allowDocumentAddition: false,
    allowDocumentDeletion: false,
    allowDocumentDownload: true,
    allowDocumentUpdate: false,
    allowDocumentUpload: false,
    recipientType: "EMAIL"
  };
}

function createEmailForIndustry(runTag, industryKey, domain) {
  const cleanDomain = domain.trim().toLowerCase();
  return `demo-${sanitizeKey(industryKey)}-${runTag}@${cleanDomain}`;
}

function createIdNumber() {
  let digits = "";
  for (let i = 0; i < 13; i += 1) {
    digits += String(randomInt(0, 9));
  }
  return digits;
}

function createRegistrationNumber(runTag, index) {
  return `${runTag.slice(-6)}${toTwoDigits(index + 1)}`;
}

function organizationName(industry, runTag) {
  return `${industry.orgPrefix} ${runTag.slice(-4)}`;
}

async function requestJson(baseUrl, endpoint, method, payload, bearerToken) {
  const headers = {
    "Content-Type": "application/json"
  };

  if (bearerToken) {
    headers.Authorization = `Bearer ${bearerToken}`;
  }

  const response = await fetch(`${baseUrl}${endpoint}`, {
    method,
    headers,
    body: payload ? JSON.stringify(payload) : undefined
  });

  const bodyText = await response.text();
  let body = null;
  if (bodyText) {
    try {
      body = JSON.parse(bodyText);
    } catch (_) {
      body = bodyText;
    }
  }

  return { ok: response.ok, status: response.status, body };
}

async function provisionIndustryAccount(baseUrl, industry, runTag, password, domain, industryIndex) {
  const email = createEmailForIndustry(runTag, industry.key, domain);
  const signUpInitiationPayload = { email };
  const signUpCompletionPayload = {
    email,
    otp: HARDCODED_OTP,
    password,
    confirmationPassword: password
  };

  log(`Creating user for ${industry.name}: ${email}`);

  const signUpInitiated = await requestJson(
    baseUrl,
    "/auth/sign-up/initiation",
    "POST",
    signUpInitiationPayload
  );

  if (!signUpInitiated.ok) {
    throw new Error(`Sign-up initiation failed (HTTP ${signUpInitiated.status})`);
  }

  const signUpCompleted = await requestJson(
    baseUrl,
    "/auth/sign-up/completion",
    "POST",
    signUpCompletionPayload
  );

  if (!signUpCompleted.ok) {
    throw new Error(`Sign-up completion failed (HTTP ${signUpCompleted.status})`);
  }

  const signInInitiated = await requestJson(baseUrl, "/auth/sign-in/initiate", "POST", {
    email,
    password
  });

  if (!signInInitiated.ok) {
    throw new Error(`Sign-in initiation failed (HTTP ${signInInitiated.status})`);
  }

  const mfaSessionId = signInInitiated.body && (signInInitiated.body.mfaSessionId || signInInitiated.body.id);
  if (!mfaSessionId) {
    throw new Error("Sign-in initiation response did not contain mfaSessionId.");
  }

  const signInCompleted = await requestJson(baseUrl, "/auth/sign-in/completion", "POST", {
    email,
    otp: HARDCODED_OTP,
    mfaSessionId
  });

  if (!signInCompleted.ok) {
    throw new Error(`Sign-in completion failed (HTTP ${signInCompleted.status})`);
  }

  const token = signInCompleted.body && signInCompleted.body.token;
  if (!token) {
    throw new Error("Sign-in completion response did not contain token.");
  }

  const personPayload = {
    firstName: industry.contactFirstName,
    lastName: `${industry.contactLastName}${toTwoDigits(industryIndex + 1)}`,
    idType: "ID_NUMBER",
    idNumber: createIdNumber()
  };

  const personRegistration = await requestJson(
    baseUrl,
    "/entity-registration/person",
    "POST",
    personPayload,
    token
  );

  if (!personRegistration.ok) {
    throw new Error(`Person registration failed (HTTP ${personRegistration.status})`);
  }

  const organizationPayload = {
    name: organizationName(industry, runTag),
    registrationNumber: createRegistrationNumber(runTag, industryIndex)
  };

  const organizationRegistration = await requestJson(
    baseUrl,
    "/entity-registration/organization",
    "POST",
    organizationPayload,
    token
  );

  if (!organizationRegistration.ok) {
    throw new Error(`Organization registration failed (HTTP ${organizationRegistration.status})`);
  }

  log(`Provisioned ${industry.name}: person + organization created.`);
  return { token, email };
}

async function generateDemoSessions() {
  const baseUrl = baseUrlInput.value.trim().replace(/\/$/, "");
  const emailDomain = emailDomainInput.value.trim();
  const password = passwordInput.value;
  const sessionsPerIndustry = Number(sessionsInput.value);
  const delayMs = Number(delayInput.value);

  if (!baseUrl) {
    setStatus("Base URL is required.", true);
    return;
  }

  if (!emailDomain || !emailDomain.includes(".")) {
    setStatus("Enter a valid email domain, for example doc-hyphen.com.", true);
    return;
  }

  if (!password || password.length < 8) {
    setStatus("Password is required and must be at least 8 characters.", true);
    return;
  }

  if (!Number.isFinite(sessionsPerIndustry) || sessionsPerIndustry < 1) {
    setStatus("Sessions per industry must be a number greater than 0.", true);
    return;
  }

  if (!Number.isFinite(delayMs) || delayMs < 0) {
    setStatus("Delay must be a number greater than or equal to 0.", true);
    return;
  }

  generateBtn.disabled = true;
  setStatus("Generating sessions...");

  const totals = {
    attempted: 0,
    successful: 0,
    failed: 0
  };

  const failures = [];
  const generatedAccounts = [];
  const targetTotal = industries.length * sessionsPerIndustry;
  const runTag = nowRunTag();

  log(`Starting generation: ${sessionsPerIndustry} sessions per industry (${targetTotal} total).`);
  log(`OTP mode: using hardcoded OTP ${HARDCODED_OTP} for sign-up/sign-in completion.`);

  for (let industryIndex = 0; industryIndex < industries.length; industryIndex += 1) {
    const industry = industries[industryIndex];
    log(`--- Industry: ${industry.name} ---`);

    let token = "";

    try {
      const provisioned = await provisionIndustryAccount(
        baseUrl,
        industry,
        runTag,
        password,
        emailDomain,
        industryIndex
      );
      token = provisioned.token;
      generatedAccounts.push({ industry: industry.name, email: provisioned.email });
      log(`Authenticated ${provisioned.email}`);
    } catch (error) {
      const setupMessage = `Setup failed for ${industry.name}: ${error.message}`;
      log(setupMessage);

      for (let i = 1; i <= sessionsPerIndustry; i += 1) {
        totals.attempted += 1;
        totals.failed += 1;
        failures.push({
          industry: industry.name,
          session: i,
          status: "setup",
          body: setupMessage
        });
      }
      continue;
    }

    for (let i = 1; i <= sessionsPerIndustry; i += 1) {
      const payload = buildSessionPayload(industry, i, sessionsPerIndustry);
      totals.attempted += 1;

      setStatus(`Generating ${totals.attempted}/${targetTotal}...`);

      try {
        const result = await requestJson(baseUrl, "/exchanges", "POST", payload, token);

        if (result.ok) {
          totals.successful += 1;
          const generatedId = result.body && result.body.id ? result.body.id : "(id not returned)";
          log(`OK ${industry.key} #${toTwoDigits(i)} -> ${generatedId}`);
        } else {
          totals.failed += 1;
          failures.push({
            industry: industry.name,
            session: i,
            status: result.status,
            body: result.body
          });
          log(`FAIL ${industry.key} #${toTwoDigits(i)} -> HTTP ${result.status}`);
        }
      } catch (error) {
        totals.failed += 1;
        failures.push({
          industry: industry.name,
          session: i,
          status: "network",
          body: error.message
        });
        log(`FAIL ${industry.key} #${toTwoDigits(i)} -> ${error.message}`);
      }

      if (delayMs > 0) {
        await sleep(delayMs);
      }
    }
  }

  if (totals.failed === 0) {
    setStatus(`Done. Created ${totals.successful}/${totals.attempted} sessions successfully.`);
  } else {
    setStatus(
      `Done with errors. Success: ${totals.successful}, Failed: ${totals.failed}. See log for details.`,
      true
    );
    log("--- Failure Summary ---");
    failures.slice(0, 20).forEach((failure) => {
      log(
        `${failure.industry} session ${toTwoDigits(failure.session)}: ${failure.status} ${JSON.stringify(failure.body)}`
      );
    });
    if (failures.length > 20) {
      log(`... ${failures.length - 20} additional failures not shown in summary.`);
    }
  }

  log("--- Generated Account Emails ---");
  if (generatedAccounts.length === 0) {
    log("No accounts were provisioned.");
  } else {
    generatedAccounts.forEach((account, index) => {
      log(`${index + 1}. ${account.industry}: ${account.email}`);
    });
  }

  generateBtn.disabled = false;
}

generateBtn.addEventListener("click", () => {
  void generateDemoSessions();
});

clearBtn.addEventListener("click", () => {
  logNode.textContent = "";
  setStatus("Idle");
});

