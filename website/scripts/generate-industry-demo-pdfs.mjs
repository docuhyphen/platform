import fs from "node:fs";
import path from "node:path";

const outputDir = path.resolve(process.cwd(), "public", "demo-pdfs");

const PAGE = { width: 595, height: 842 };

const palette = {
  primary: "#20344d",
  text: "#223244",
  muted: "#667488",
  border: "#d7e0ea",
  surface: "#f5f8fc",
};

const popPalette = {
  lightGreen: "#d9f4de",
  softYellow: "#fff3bf",
  smallRed: "#f8cdcd",
};

function clampChannel(value) {
  return Math.max(0, Math.min(255, value));
}

function shadeHex(hex, factor) {
  const cleaned = hex.replace("#", "");
  const r = parseInt(cleaned.slice(0, 2), 16);
  const g = parseInt(cleaned.slice(2, 4), 16);
  const b = parseInt(cleaned.slice(4, 6), 16);

  const to = factor >= 0 ? 255 : 0;
  const amount = Math.abs(factor);

  const nextR = clampChannel(Math.round(r + (to - r) * amount));
  const nextG = clampChannel(Math.round(g + (to - g) * amount));
  const nextB = clampChannel(Math.round(b + (to - b) * amount));

  return `#${nextR.toString(16).padStart(2, "0")}${nextG.toString(16).padStart(2, "0")}${nextB.toString(16).padStart(2, "0")}`;
}

const industries = [
  {
    slug: "law-firms-legal-practices",
    title: "Law Firms & Legal Practices",
    subtitle: "Case File Summary: Orion Facilities Group v. Meridian Build Systems",
    summary:
      "Prepared by Halbrook, Vance & Mercer LLP under case number HVM-CIV-26-1189. The matter concerns an alleged breach of a commercial construction services agreement and is currently in early litigation discovery.",
    kpis: [
      { label: "Evidence Items", value: "7" },
      { label: "Key Parties", value: "4" },
      { label: "Timeline Events", value: "7" },
      { label: "Status", value: "Early Discovery" },
    ],
    tableTitle: "Evidence Index",
    tableColumns: ["Evidence ID", "Document", "Type", "Status"],
    tableRows: [
      ["EV-001", "Master Services Agreement", "Contract", "Admitted"],
      ["EV-014", "Scope Revision Packet #1", "Email", "Under Review"],
      ["EV-031", "Notice of Breach Letter", "Notice", "Admitted"],
      ["EV-039", "Revised Recovery Schedule", "Project Plan", "Challenged"],
      ["EV-055", "Filed Complaint & Exhibits", "Court Filing", "Accepted"],
    ],
    barChart: {
      title: "Monthly Litigation Activity (Q1-Q2 2026)",
      labels: ["Jan", "Feb", "Mar", "Apr", "May", "Jun"],
      values: [6, 8, 9, 7, 10, 11],
      color: "#4b6496",
    },
    lineGraph: {
      title: "Open Legal Issues Progression",
      labels: ["Week 1", "Week 2", "Week 3", "Week 4", "Week 5", "Week 6"],
      values: [12, 11, 9, 8, 7, 6],
      color: "#0f766e",
      ySuffix: "",
    },
    pageColor: "#f1f7ff",
  },
  {
    slug: "real-estate-property-management",
    title: "Real Estate & Property Management",
    subtitle: "Property Investment Report: Harbor View Residences",
    summary:
      "Prepared for Lattice Peak Capital Partners. The 24-unit asset at 1187 Seabrook Avenue is modeled with projected annual rental income of R 552,660 and NOI of R 321,160.",
    kpis: [
      { label: "Purchase Price", value: "R 4.85M" },
      { label: "Projected NOI", value: "R 321,160" },
      { label: "Cap Rate", value: "6.62%" },
      { label: "Occupancy", value: "94% blended" },
    ],
    tableTitle: "Market Comparison",
    tableColumns: ["Comparable", "Distance", "Price", "Cap Rate"],
    tableRows: [
      ["Maple Court Apartments", "0.8 mi", "R 4,200,000", "6.35%"],
      ["Riverside Lofts", "1.1 mi", "R 5,600,000", "6.10%"],
      ["Oakline Residences", "1.4 mi", "R 4,700,000", "6.55%"],
      ["Harbor View Residences", "Subject", "R 4,850,000", "6.62%"],
      ["Submarket Median", "N/A", "R 4,760,000", "6.40%"],
    ],
    barChart: {
      title: "Projected Annual Income by Unit Type (R '000)",
      labels: ["Jan", "Feb", "Mar", "Apr", "May", "Jun"],
      values: [186, 245, 121, 198, 252, 125],
      color: "#4b678c",
    },
    lineGraph: {
      title: "Quarterly Occupancy Rate",
      labels: ["W1", "W2", "W3", "W4", "W5", "W6"],
      values: [92, 93, 94, 94, 95, 95],
      color: "#24505a",
      ySuffix: "%",
    },
    pageColor: "#f5fbfa",
  },
  {
    slug: "accounting-audit-firms",
    title: "Accounting & Audit Firms",
    subtitle: "Quarterly Financial Report: Northstar Industrial Components Ltd.",
    summary:
      "Prepared by Alder & Finch Advisory LLP for Q1 FY2026. Revenue reached R 8.5M with stable margins and improved working capital performance.",
    kpis: [
      { label: "Revenue", value: "R 8.50M" },
      { label: "Gross Margin", value: "41.8%" },
      { label: "Net Margin", value: "23.8%" },
      { label: "Current Ratio", value: "1.84" },
    ],
    tableTitle: "Profit and Loss Summary",
    tableColumns: ["Item", "Amount (R)"],
    tableRows: [
      ["Revenue", "8,500,000"],
      ["Cost of Goods Sold", "4,950,000"],
      ["Gross Profit", "3,550,000"],
      ["Operating Expenses", "950,000"],
      ["Net Profit", "2,020,000"],
    ],
    barChart: {
      title: "Revenue by Department (R '000)",
      labels: ["Jan", "Feb", "Mar", "Apr", "May", "Jun"],
      values: [4280, 1640, 1120, 890, 570, 8500],
      color: "#5c6da9",
    },
    lineGraph: {
      title: "Quarterly Net Profit Trend (R '000)",
      labels: ["Sprint 1", "2", "3", "4", "5", "6"],
      values: [1680, 1810, 1940, 1890, 1965, 2020],
      color: "#8e7cc3",
      ySuffix: "",
    },
    pageColor: "#f6f4fc",
  },
  {
    slug: "healthcare-medical-practices",
    title: "Healthcare & Medical Practices",
    subtitle: "Patient Referral Summary: Cedar Grove Family Medicine",
    summary:
      "Referring physician Dr. Amelia R. Nolan, MD has referred Sofia Lynn Hart for specialist gastroenterology evaluation due to persistent upper abdominal symptoms, mild anemia, and positive occult blood screening.",
    kpis: [
      { label: "Patient Age", value: "47" },
      { label: "Weight Change", value: "-3.2 kg" },
      { label: "FOBT", value: "Positive (1/3)" },
      { label: "Priority", value: "Expedited Ref" },
    ],
    tableTitle: "Recent Test Results",
    tableColumns: ["Test", "Date", "Result", "Notes"],
    tableRows: [
      ["CBC", "11 Mar 2026", "Mild microcytic anemia", "Hgb 11.1 g/dL"],
      ["CMP", "11 Mar 2026", "Within normal limits", "No liver concerns"],
      ["H. pylori Breath", "12 Mar 2026", "Negative", "PPI held 2 weeks"],
      ["FOBT", "13 Mar 2026", "Positive", "1 of 3 cards"],
      ["Abdominal Ultrasound", "14 Mar 2026", "No acute findings", "Post-cholecystectomy"],
    ],
    barChart: {
      title: "Weekly Symptom Severity Score",
      labels: ["W1", "W2", "W3", "W4", "W5", "W6"],
      values: [8, 8, 7, 7, 6, 6],
      color: "#3f8391",
    },
    lineGraph: {
      title: "Clinical Follow-up Timeline (days)",
      labels: ["W1", "W2", "W3", "W4", "W5", "W6"],
      values: [18, 16, 14, 12, 10, 8],
      color: "#4fb3a8",
      ySuffix: "d",
    },
    pageColor: "#f3fbfb",
  },
  {
    slug: "banks-lending-institutions",
    title: "Banks & Lending Institutions",
    subtitle: "Credit Risk Assessment: Meridian Trust Bank",
    summary:
      "Application MTB-CLA-2026-0417 for Daniel M. Kessler was evaluated with annual income of R 128,000, credit score 722, requested loan amount of R 285,000, and moderate-low risk profile.",
    kpis: [
      { label: "Credit Score", value: "722" },
      { label: "DTI", value: "27.4%" },
      { label: "LTV", value: "76.0%" },
      { label: "Decision", value: "Approved" },
    ],
    tableTitle: "Debt Obligations",
    tableColumns: ["Creditor", "Balance", "Monthly Payment"],
    tableRows: [
      ["Horizon Auto Finance", "R 18,400", "R 465"],
      ["Summit Card Services", "R 6,950", "R 210"],
      ["Collegiate Lending Corp", "R 24,700", "R 390"],
      ["Metro Home Furnishings", "R 2,180", "R 95"],
      ["Total Revolving + Installment", "R 52,230", "R 1,160"],
    ],
    barChart: {
      title: "Monthly Debt Obligation Mix (R)",
      labels: ["Jan", "Feb", "Mar", "Apr", "May", "Jun"],
      values: [465, 210, 390, 95, 760, 1160],
      color: "#3f6ba1",
    },
    lineGraph: {
      title: "Risk Assessment Trend",
      labels: ["Week 1", "2", "3", "4", "5", "6"],
      values: [64, 62, 60, 59, 57, 55],
      color: "#243f63",
      ySuffix: "",
    },
    pageColor: "#f3f6fb",
  },
];

function ensureOutputDir() {
  fs.mkdirSync(outputDir, { recursive: true });
}

function toRgb01(hex) {
  const cleaned = hex.replace("#", "");
  const r = parseInt(cleaned.slice(0, 2), 16) / 255;
  const g = parseInt(cleaned.slice(2, 4), 16) / 255;
  const b = parseInt(cleaned.slice(4, 6), 16) / 255;
  return `${r.toFixed(3)} ${g.toFixed(3)} ${b.toFixed(3)}`;
}

function pdfY(top) {
  return PAGE.height - top;
}

function esc(text) {
  return String(text).replace(/\\/g, "\\\\").replace(/\(/g, "\\(").replace(/\)/g, "\\)");
}

function drawFilledRect(parts, x, top, width, height, color) {
  const y = pdfY(top + height);
  parts.push(`${toRgb01(color)} rg`);
  parts.push(`${x.toFixed(2)} ${y.toFixed(2)} ${width.toFixed(2)} ${height.toFixed(2)} re f`);
}

function drawStrokedRect(parts, x, top, width, height, color, strokeWidth = 1) {
  const y = pdfY(top + height);
  parts.push(`${toRgb01(color)} RG`);
  parts.push(`${strokeWidth.toFixed(2)} w`);
  parts.push(`${x.toFixed(2)} ${y.toFixed(2)} ${width.toFixed(2)} ${height.toFixed(2)} re S`);
}

function drawLine(parts, x1, top1, x2, top2, color, strokeWidth = 1) {
  parts.push(`${toRgb01(color)} RG`);
  parts.push(`${strokeWidth.toFixed(2)} w`);
  parts.push(`${x1.toFixed(2)} ${pdfY(top1).toFixed(2)} m ${x2.toFixed(2)} ${pdfY(top2).toFixed(2)} l S`);
}

function drawText(parts, text, x, top, font, size, color = palette.text) {
  parts.push("BT");
  parts.push(`/${font} ${size} Tf`);
  parts.push(`${toRgb01(color)} rg`);
  parts.push(`1 0 0 1 ${x.toFixed(2)} ${pdfY(top).toFixed(2)} Tm`);
  parts.push(`(${esc(text)}) Tj`);
  parts.push("ET");
}

function splitText(text, maxCharsPerLine) {
  const words = text.split(" ");
  const lines = [];
  let current = "";

  words.forEach((word) => {
    const next = current ? `${current} ${word}` : word;
    if (next.length > maxCharsPerLine) {
      lines.push(current);
      current = word;
    } else {
      current = next;
    }
  });

  if (current) {
    lines.push(current);
  }

  return lines;
}

function drawKpiCards(parts, cards, accentColor) {
  const startX = 30;
  const top = 102;
  const cardWidth = 124;
  const cardHeight = 58;
  const gap = 12;
  const stripeColor = shadeHex(accentColor, -0.1);
  const cardColor = shadeHex(accentColor, 0.9);

  cards.forEach((card, index) => {
    const x = startX + index * (cardWidth + gap);
    const cardFill = index === 1 ? popPalette.softYellow : index === 3 ? popPalette.lightGreen : cardColor;
    const stripeFill = index === 2 ? popPalette.smallRed : stripeColor;
    drawFilledRect(parts, x, top, cardWidth, cardHeight, cardFill);
    drawFilledRect(parts, x, top, cardWidth, 6, stripeFill);
    drawStrokedRect(parts, x, top, cardWidth, cardHeight, palette.border);
    drawText(parts, card.label, x + 8, top + 22, "F1", 9, palette.muted);
    drawText(parts, card.value, x + 8, top + 43, "F2", 14, shadeHex(accentColor, -0.22));
  });
}

function drawTable(parts, columns, rows, accentColor) {
  const x = 30;
  const top = 390;
  const width = 535;
  const rowHeight = 26;
  const columnWidth = width / columns.length;

  drawFilledRect(parts, x, top, width, rowHeight, shadeHex(accentColor, 0.86));
  drawStrokedRect(parts, x, top, width, rowHeight * (rows.length + 1), shadeHex(accentColor, 0.56));

  columns.forEach((column, index) => {
    drawText(parts, column, x + index * columnWidth + 7, top + 17, "F2", 9, palette.primary);
  });

  rows.forEach((row, rowIndex) => {
    const rowTop = top + rowHeight * (rowIndex + 1);
    if (rowIndex % 2 === 0) {
      drawFilledRect(parts, x, rowTop, width, rowHeight, shadeHex(accentColor, 0.93));
    }
    row.forEach((cell, colIndex) => {
      drawText(parts, cell, x + colIndex * columnWidth + 7, rowTop + 17, "F1", 9, palette.text);
    });
    drawLine(parts, x, rowTop + rowHeight, x + width, rowTop + rowHeight, shadeHex(accentColor, 0.6));
  });

  for (let i = 1; i < columns.length; i += 1) {
    const lx = x + i * columnWidth;
    drawLine(parts, lx, top, lx, top + rowHeight * (rows.length + 1), shadeHex(accentColor, 0.6));
  }
}

function drawBarChart(parts, chart) {
  const x = 30;
  const top = 196;
  const width = 256;
  const height = 170;
  const { labels, values, color } = chart;
  const max = Math.max(...values);
  const min = Math.min(...values);

  drawFilledRect(parts, x, top + 20, width, height - 20, shadeHex(color, 0.9));
  drawLine(parts, x + 20, top + 24, x + 20, top + 138, shadeHex(color, 0.45));
  drawLine(parts, x + 20, top + 138, x + 238, top + 138, shadeHex(color, 0.45));

  const usableWidth = 204;
  const barGap = 8;
  const barWidth = (usableWidth - barGap * (labels.length - 1)) / labels.length;

  values.forEach((value, index) => {
    const barHeight = (value / max) * 95;
    const barX = x + 28 + index * (barWidth + barGap);
    const barTop = top + 138 - barHeight;
    let barColor = index % 2 === 0 ? shadeHex(color, 0.08) : shadeHex(color, -0.1);
    if (index === values.length - 1) barColor = popPalette.lightGreen;
    if (index === 1) barColor = popPalette.softYellow;
    if (value === min) barColor = popPalette.smallRed;

    drawFilledRect(parts, barX, barTop, barWidth, barHeight, barColor);
    drawText(parts, String(value), barX - 1, barTop - 4, "F1", 8, palette.text);
    drawText(parts, labels[index], barX - 1, top + 153, "F1", 8, palette.muted);
  });

  drawStrokedRect(parts, x, top + 20, width, height - 20, shadeHex(color, 0.38));
}

function drawLineChart(parts, chart) {
  const x = 306;
  const top = 196;
  const width = 259;
  const height = 170;
  const { labels, values, color, ySuffix } = chart;
  const max = Math.max(...values);
  const min = Math.min(...values);
  const range = Math.max(1, max - min);

  drawFilledRect(parts, x, top + 20, width, height - 20, shadeHex(color, 0.92));
  drawLine(parts, x + 16, top + 24, x + 16, top + 138, shadeHex(color, 0.45));
  drawLine(parts, x + 16, top + 138, x + 240, top + 138, shadeHex(color, 0.45));

  const points = values.map((value, index) => {
    const px = x + 20 + (index * 216) / (values.length - 1);
    const py = top + 138 - ((value - min) / range) * 95;
    return { px, py, value, label: labels[index] };
  });

  parts.push(`${toRgb01(shadeHex(color, 0.72))} rg`);
  parts.push(`${points[0].px.toFixed(2)} ${pdfY(top + 138).toFixed(2)} m`);
  points.forEach((point) => {
    parts.push(`${point.px.toFixed(2)} ${pdfY(point.py).toFixed(2)} l`);
  });
  parts.push(`${points[points.length - 1].px.toFixed(2)} ${pdfY(top + 138).toFixed(2)} l`);
  parts.push("f");

  parts.push(`${toRgb01(color)} RG`);
  parts.push("2.2 w");
  points.forEach((point, index) => {
    const command = `${point.px.toFixed(2)} ${pdfY(point.py).toFixed(2)} ${index === 0 ? "m" : "l"}`;
    parts.push(command);
  });
  parts.push("S");

  points.forEach((point) => {
    const pointColor = point.value === min ? popPalette.smallRed : shadeHex(color, -0.04);
    drawFilledRect(parts, point.px - 2.5, point.py - 2.5, 5, 5, pointColor);
    drawText(parts, `${point.value}${ySuffix}`, point.px - 11, point.py - 7, "F1", 8, palette.text);
    drawText(parts, point.label, point.px - 10, top + 153, "F1", 8, palette.muted);
  });

  drawStrokedRect(parts, x, top + 20, width, height - 20, shadeHex(color, 0.42));
}

function createPdfBuffer(contentStream) {
  const objects = [];

  const addObject = (body) => {
    objects.push(body);
    return objects.length;
  };

  const font1 = addObject("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
  const font2 = addObject("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>");

  const content = addObject(
    `<< /Length ${Buffer.byteLength(contentStream, "utf8")} >>\nstream\n${contentStream}\nendstream`,
  );

  const page = addObject(
    `<< /Type /Page /Parent 5 0 R /MediaBox [0 0 ${PAGE.width} ${PAGE.height}] /Resources << /Font << /F1 ${font1} 0 R /F2 ${font2} 0 R >> >> /Contents ${content} 0 R >>`,
  );

  const pages = addObject(`<< /Type /Pages /Count 1 /Kids [${page} 0 R] >>`);
  const catalog = addObject(`<< /Type /Catalog /Pages ${pages} 0 R >>`);

  let pdf = "%PDF-1.4\n";
  const offsets = [0];

  objects.forEach((obj, index) => {
    offsets.push(Buffer.byteLength(pdf, "utf8"));
    pdf += `${index + 1} 0 obj\n${obj}\nendobj\n`;
  });

  const xrefStart = Buffer.byteLength(pdf, "utf8");
  pdf += `xref\n0 ${objects.length + 1}\n`;
  pdf += "0000000000 65535 f \n";

  for (let i = 1; i <= objects.length; i += 1) {
    pdf += `${String(offsets[i]).padStart(10, "0")} 00000 n \n`;
  }

  pdf += `trailer\n<< /Size ${objects.length + 1} /Root ${catalog} 0 R >>\nstartxref\n${xrefStart}\n%%EOF`;
  return Buffer.from(pdf, "utf8");
}

function buildIndustryPdf(industry) {
  const filePath = path.join(outputDir, `${industry.slug}.pdf`);
  const parts = [];
  const accentColor = industry.lineGraph.color;

  drawFilledRect(parts, 0, 0, PAGE.width, PAGE.height, industry.pageColor || "#f7f9fc");
  drawFilledRect(parts, 0, 0, PAGE.width, 8, shadeHex(accentColor, -0.08));

  splitText(industry.summary, 108).forEach((line, index) => {
    drawText(parts, line, 30, 42 + index * 15, "F1", 10, palette.text);
  });

  drawKpiCards(parts, industry.kpis, accentColor);
  drawBarChart(parts, industry.barChart);
  drawLineChart(parts, industry.lineGraph);
  drawTable(parts, industry.tableColumns, industry.tableRows, accentColor);

  const pdfBuffer = createPdfBuffer(parts.join("\n"));
  fs.writeFileSync(filePath, pdfBuffer);
  return filePath;
}

function main() {
  ensureOutputDir();

  const generatedFiles = industries.map((industry) => buildIndustryPdf(industry));

  generatedFiles.forEach((file) => {
    const stat = fs.statSync(file);
    console.log(`${path.basename(file)} (${Math.round(stat.size / 1024)} KB)`);
  });
}

main();

