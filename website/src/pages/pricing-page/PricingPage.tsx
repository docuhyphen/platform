import {
    Button,
    Table,
    TableBody,
    TableCell,
    TableHeader,
    TableHeaderCell,
    TableRow,
    Text,
} from "@fluentui/react-components";
import {useState} from "react";
import {PageShell} from "../../shared/page-shell/PageShell.tsx";
import {SIGN_UP_URL} from "../../landing/shared.ts";
import {featureRows, pricingPlans} from "./PricingPageData.ts";
import type {BillingFrequency} from "./PricingPageData.ts";
import {PricingFeatureValue} from "./PricingFeatureValue.tsx";
import {PricingIntroduction} from "./PricingIntroduction.tsx";
import {PricingNotes} from "./PricingNotes.tsx";
import {PricingPlanHeaderCell} from "./PricingPlanHeaderCell.tsx";
import {usePricingPageStyles} from "./PricingPageStyles.tsx";

export function PricingPage()
{
    const styles = usePricingPageStyles();
    const [billingFrequency, setBillingFrequency] = useState<BillingFrequency>("annual");

    return (
        <PageShell>
            <div id={"pricing-page"}
                 className={styles.page}>
                <PricingIntroduction billingFrequency={billingFrequency}
                                     onBillingFrequencyChange={setBillingFrequency}/>

                <section id={"pricing-comparison"}
                         className={styles.comparison}
                         aria-label="Subscription comparison">
                    <div id={"pricing-table-viewport"}
                         className={styles.tableViewport}
                         role="region"
                         aria-label="Scrollable subscription comparison"
                         tabIndex={0}>
                        <Table id={"pricing-table"}
                               className={styles.table}
                               aria-label="DocuHyphen subscription plans">
                            <TableHeader id={"pricing-table-header"}>
                                <TableRow id={"pricing-table-header-row"}>
                                    <TableHeaderCell id={"pricing-feature-header"}
                                                     className={styles.featureHeader}>
                                        <div id={"pricing-feature-header-content"}
                                             className={styles.featureHeaderContent}>
                                            <Text id={"pricing-feature-header-label"}>Plans and features</Text>
                                            <Button id={"pricing-sign-up-action"}
                                                    as="a"
                                                    href={SIGN_UP_URL}
                                                    appearance="primary"
                                                    shape="circular"
                                                    className={styles.featureAction}>
                                                Sign up
                                            </Button>
                                        </div>
                                    </TableHeaderCell>
                                    {pricingPlans.map((plan) => (
                                        <PricingPlanHeaderCell key={plan.name}
                                                               plan={plan}
                                                               billingFrequency={billingFrequency}/>
                                    ))}
                                </TableRow>
                            </TableHeader>
                            <TableBody id={"pricing-table-body"}>
                                {featureRows.map(([feature, free, personal, business]) => (
                                    <TableRow id={`pricing-feature-row-${feature}`}
                                              key={feature}>
                                        <TableHeaderCell id={`pricing-feature-cell-${feature}`}
                                                         className={styles.featureCell}>{feature}</TableHeaderCell>
                                        {[free, personal, business].map((value, index) => (
                                            <TableCell id={`pricing-value-cell-${feature}-${index}`}
                                                       key={`${feature}-${index}`}>
                                                <PricingFeatureValue id={`pricing-value-${feature}-${index}`}
                                                                     value={value}/>
                                            </TableCell>
                                        ))}
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </div>
                    <PricingNotes/>
                </section>
            </div>
        </PageShell>
    );
}
