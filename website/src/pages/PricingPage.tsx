import {
    Button,
    Table,
    TableBody,
    TableCell,
    TableHeader,
    TableHeaderCell,
    TableRow,
    Switch,
    Text,
    Title2,
} from "@fluentui/react-components";
import {useState} from "react";
import {PageShell} from "../shared/PageShell.tsx";
import {SIGN_UP_URL} from "../landing/shared.ts";
import {featureRows, pricingPlans} from "./PricingPageData.ts";
import type {BillingFrequency} from "./PricingPageData.ts";
import {usePricingPageStyles} from "./PricingPageStyles.tsx";

export function PricingPage()
{
    const styles = usePricingPageStyles();
    const [billingFrequency, setBillingFrequency] = useState<BillingFrequency>("annual");

    return (
        <PageShell>
            <div
                id="pricing-page"
                className={styles.page}
            >
                <section
                    id="pricing-introduction"
                    className={styles.introduction}
                    aria-labelledby="pricing-page-title"
                >
                    <Title2
                        id="pricing-page-title"
                        className={styles.heading}
                    >
                        Simple pricing for secure document Exchanges
                    </Title2>
                    <Text
                        id="pricing-page-description"
                        className={styles.description}
                        size={500}
                    >
                        Pay only for your team. Invite as many external clients as you need, and let them respond
                        securely without creating an account.
                    </Text>
                    <div
                        id="pricing-billing-options"
                        className={styles.billingOptions}
                        aria-label="Billing frequency"
                    >
                        <Switch
                            id="pricing-billing-switch"
                            checked={billingFrequency === "annual"}
                            label={billingFrequency === "annual"
                                ? "Annual billing, save up to 17%"
                                : "Month-to-month billing"}
                            labelPosition="after"
                            onChange={(_, data) => setBillingFrequency(data.checked ? "annual" : "monthly")}
                        />
                    </div>
                </section>

                <section
                    id="pricing-comparison"
                    className={styles.comparison}
                    aria-label="Subscription comparison"
                >
                    <div
                        id="pricing-table-viewport"
                        className={styles.tableViewport}
                        role="region"
                        aria-label="Scrollable subscription comparison"
                        tabIndex={0}
                    >
                        <Table
                            id="pricing-table"
                            className={styles.table}
                            aria-label="DocuHyphen subscription plans"
                        >
                            <TableHeader>
                                <TableRow>
                                    <TableHeaderCell className={styles.featureHeader}>
                                        <div className={styles.featureHeaderContent}>
                                            <Text>Plans and features</Text>
                                            <Button
                                                id="pricing-sign-up-action"
                                                as="a"
                                                href={SIGN_UP_URL}
                                                appearance="primary"
                                                shape="circular"
                                                className={styles.featureAction}
                                            >
                                                Sign up
                                            </Button>
                                        </div>
                                    </TableHeaderCell>
                                    {pricingPlans.map((plan) => (
                                        <TableHeaderCell
                                            key={plan.name}
                                            className={styles.planHeader}
                                        >
                                            <div className={styles.planHeading}>
                                                <div className={styles.planNameRow}>
                                                    <Text className={styles.planName}>{plan.name}</Text>
                                                </div>
                                                <Text className={styles.planDescription} align="center">
                                                    {plan.description}
                                                </Text>
                                                <div className={styles.priceRow}>
                                                    <Text className={styles.price}>
                                                        {billingFrequency === "annual" ? plan.annualPrice : plan.monthlyPrice}
                                                    </Text>
                                                    <Text className={styles.cadence}>{plan.cadence}</Text>
                                                </div>
                                                <Text className={styles.billingDetail}
                                                      align={"center"}>
                                                    {billingFrequency === "annual" ? plan.annualDetail : plan.monthlyDetail}
                                                </Text>
                                            </div>
                                        </TableHeaderCell>
                                    ))}
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {featureRows.map(([feature, free, personal, business]) => (
                                    <TableRow key={feature}>
                                        <TableHeaderCell className={styles.featureCell}>{feature}</TableHeaderCell>
                                        {[free, personal, business].map((value, index) => (
                                            <TableCell key={`${feature}-${index}`}>
                                                {value === "Included" || value === "Not included"
                                                    ? <strong>{value}</strong>
                                                    : value}
                                            </TableCell>
                                        ))}
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </div>
                    <div className={styles.notes}>
                        <Text><strong>External clients are not seats.</strong> Clients can complete Exchanges without
                            joining your organization or purchasing an account.</Text>
                        <Text><strong>Business billing stays simple.</strong> Every active organization member is one
                            paid seat.</Text>
                        <Text><strong>Prices are in rand.</strong> Published prices include 15% VAT where DocuHyphen is
                            required to charge VAT.</Text>
                    </div>
                </section>
            </div>
        </PageShell>
    );
}
