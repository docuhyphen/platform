import {
    Button,
    Title2,
    Text,
    Card,
    CardHeader,
} from "@fluentui/react-components";
import AppLogo from "./app-logo/AppLogo.tsx";
import {appStyles} from "./AppStyles.tsx";

export default function App()
{
    const styles = appStyles();

    return (
        <main className={styles.page}>
            <section className={styles.heroContainer}>
                <section className={styles.hero}>
                    <section className={styles.appLogo}>
                        <AppLogo/>
                    </section>
                    <Title2 align="center" className={styles.gradientTitle}>
                        Welcome to{" "}
                        <span className={styles.noWrap}>docu-hyphen</span>, secure document sharing for sensitive
                        business data
                    </Title2>
                    <Text size={400} align={"center"}>
                        Request, send, and track sensitive documents with encryption,
                        verification, and full audit trails.
                    </Text>

                    <div className={styles.actions}>
                        <Button appearance="primary"
                                as="a"
                                className={styles.btnLong}
                                shape={"circular"}
                                target={"_blank"}
                                href="https://app.docuhyphen.com/sign-up">
                            Try the App
                        </Button>
                        <Button appearance="secondary"
                                as="a"
                                className={styles.btnLong}
                                target={"_blank"}
                                shape={"circular"}
                                href="https://app.docuhyphen.com/sign-in">
                            Sign In
                        </Button>
                    </div>
                </section>
            </section>

            {/* Problem */}

            <Title2 align={"center"} className={styles.sectionTitle}>The problem</Title2>
            <section className={styles.theProblemSection}>
                <div>
                    <img src={"/the-problem-image.png"} className={styles.theProblemImg}/>
                    <br/>
                    <Text italic={true}>
                        Traditional tools aren’t just inconvenient — they put sensitive information, your business, and
                        your compliance obligations at serious risk.
                    </Text>
                </div>
                <div className={styles.theProblemText}>
                    <Text>
                        Email and generic file-sharing platforms were never designed for
                        exchanging sensitive customer or business documents.
                    </Text>
                    <ul>
                        <li>
                            <p>
                                <Text weight="semibold">High risk of unauthorized access:</Text><br/>
                                Documents containing personal, financial, or legal data can easily be intercepted,
                                forwarded, or stored insecurely.
                            </p>
                        </li>
                        <li>
                            <p>
                                <Text weight="semibold">Uncontrolled document sharing:</Text><br/>
                                Traditional tools make it difficult to securely share documents with multiple parties.
                                Links
                                can be forwarded, emails sent to wrong recipients, and there’s no way to restrict access
                                or
                                verify who is receiving the files.
                            </p>
                        </li>
                        <li>
                            <p>
                                <Text weight="semibold">No reliable audit trails:</Text><br/>
                                Businesses have little to no visibility into who accessed documents, when, or whether
                                they
                                were modified.
                            </p>
                        </li>
                        <li>
                            <p>
                                <Text weight="semibold">Compliance challenges with POPIA and similar
                                    regulations:</Text><br/>
                                Without proper tracking, encryption, and access controls, companies risk violating data
                                protection laws, leading to fines or reputational damage.
                            </p>
                        </li>
                    </ul>
                </div>
            </section>

            {/* Solution */}
            <Title2 align={"center"} className={styles.sectionTitle}>The solution</Title2>
            <section className={styles.theSolutionSection}>
                <div className={styles.cards}>
                    <Card>
                        <CardHeader header={<Text weight="semibold">Request Documents</Text>}/>
                        <Text>
                            Securely request documents from customers or other companies
                            through controlled share sessions.
                        </Text>
                    </Card>

                    <Card>
                        <CardHeader header={<Text weight="semibold">Send Documents</Text>}/>
                        <Text>
                            Send financial, legal, or confidential documents without email
                            attachments or shared links.
                        </Text>
                    </Card>

                    <Card>
                        <CardHeader header={<Text weight="semibold">Encrypted by Default</Text>}/>
                        <Text>
                            Documents are encrypted during upload, storage, and download,
                            with optional end-to-end encryption.
                        </Text>
                    </Card>

                    <Card>
                        <CardHeader header={<Text weight="semibold">Audit & Compliance</Text>}/>
                        <Text>
                            Every view, upload, and download is logged for accountability
                            and regulatory compliance.
                        </Text>
                    </Card>
                </div>
            </section>

            {/* Who it's for */}
            <section className={styles.forWhoSection}>
                <Title2 align="center">
                    Who it’s for
                </Title2>

                <Text className={styles.whoForSubtitle}>
                    Built for organizations that handle sensitive documents and require security,
                    compliance, and accountability.
                </Text>

                <section className={styles.whoForGrid}>

                    {/* Financial & Professional Services */}
                    <Card className={styles.whoForCard}>
                        <CardHeader header={<Text weight="semibold">Financial & Professional Services</Text>}/>
                        <ul className={styles.whoForList}>
                            <li>
                                <Text weight="semibold">Accounting & audit firms</Text>
                                <Text size={300}>
                                    Secure exchange of tax records, financial statements, and supporting documents.
                                </Text>
                            </li>
                            <li>
                                <Text weight="semibold">Financial advisors & wealth managers</Text>
                                <Text size={300}>
                                    Protect client identity and financial data while maintaining full audit trails.
                                </Text>
                            </li>
                            <li>
                                <Text weight="semibold">Banks & lending institutions</Text>
                                <Text size={300}>
                                    Collect and share KYC, income verification, and compliance documents securely.
                                </Text>
                            </li>
                        </ul>
                    </Card>

                    {/* Insurance & Risk Management */}
                    <Card className={styles.whoForCard}>
                        <CardHeader header={<Text weight="semibold">Insurance & Risk Management</Text>}/>
                        <ul className={styles.whoForList}>
                            <li>
                                <Text weight="semibold">Insurance companies</Text>
                                <Text size={300}>
                                    Secure handling of policy documents, claims, and customer identity records.
                                </Text>
                            </li>
                            <li>
                                <Text weight="semibold">Insurance brokers & underwriters</Text>
                                <Text size={300}>
                                    Controlled document sharing between clients, insurers, and assessors.
                                </Text>
                            </li>
                            <li>
                                <Text weight="semibold">Claims management firms</Text>
                                <Text size={300}>
                                    Track document submissions with visibility and accountability.
                                </Text>
                            </li>
                        </ul>
                    </Card>

                    {/* Legal & Regulatory */}
                    <Card className={styles.whoForCard}>
                        <CardHeader header={<Text weight="semibold">Legal & Regulatory</Text>}/>
                        <ul className={styles.whoForList}>
                            <li>
                                <Text weight="semibold">Law firms & legal practices</Text>
                                <Text size={300}>
                                    Exchange contracts, affidavits, and sensitive legal documents with clients.
                                </Text>
                            </li>
                            <li>
                                <Text weight="semibold">Compliance & risk consultancies</Text>
                                <Text size={300}>
                                    Maintain verifiable audit trails for regulated workflows.
                                </Text>
                            </li>
                            <li>
                                <Text weight="semibold">Corporate secretarial services</Text>
                                <Text size={300}>
                                    Secure storage and exchange of statutory and company records.
                                </Text>
                            </li>
                        </ul>
                    </Card>

                    {/* Corporate & Enterprise Operations */}
                    <Card className={styles.whoForCard}>
                        <CardHeader header={<Text weight="semibold">Corporate & Enterprise Operations</Text>}/>
                        <ul className={styles.whoForList}>
                            <li>
                                <Text weight="semibold">Medium to large enterprises</Text>
                                <Text size={300}>
                                    Internal and external document sharing with verified parties.
                                </Text>
                            </li>
                            <li>
                                <Text weight="semibold">Procurement & vendor onboarding teams</Text>
                                <Text size={300}>
                                    Collect compliance documents from suppliers securely.
                                </Text>
                            </li>
                            <li>
                                <Text weight="semibold">HR & payroll departments</Text>
                                <Text size={300}>
                                    Exchange employee identity, tax, and contract documents safely.
                                </Text>
                            </li>
                        </ul>
                    </Card>

                    {/* Small & Growing Businesses */}
                    <Card className={styles.whoForCard}>
                        <CardHeader header={<Text weight="semibold">Small & Growing Businesses</Text>}/>
                        <ul className={styles.whoForList}>
                            <li>
                                <Text weight="semibold">SMEs handling customer data</Text>
                                <Text size={300}>
                                    A professional, secure alternative to email and generic file sharing.
                                </Text>
                            </li>
                            <li>
                                <Text weight="semibold">Startups in regulated industries</Text>
                                <Text size={300}>
                                    Compliance-ready document sharing without enterprise overhead.
                                </Text>
                            </li>
                        </ul>
                    </Card>

                </section>

            </section>
            {/* Final CTA */}
            <section className={styles.footerCta}>
                <Text weight={"regular"} size={600}>Stop sending sensitive documents by email</Text>
                <div className={styles.actions}>
                    <Button appearance="primary"
                            as="a"
                            target={"_blank"}
                            shape={"circular"}
                            className={styles.btnLong}
                            href="https://app.docuhyphen.com/sign-up">
                        Get Started
                    </Button>
                </div>
            </section>
        </main>
    );
}
