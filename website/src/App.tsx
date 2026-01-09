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
                    <img src={"/The-problem-image.png"} className={styles.theProblemImg}/>
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
            <Title2 align={"center"} className={styles.sectionTitle}>Who it’s for</Title2>
            <ul className={styles.forWhoSection}>
                <li>
                    <Text size={400}>
                        Accounting and audit firms
                    </Text>
                </li>
                <li>
                    <Text size={400}>
                        Financial services and advisors
                    </Text>
                </li>
                <li>
                    <Text size={400}>
                        Legal practices
                    </Text>
                </li>
                <li>
                    <Text size={400}>
                        Small businesses handling sensitive customer data
                    </Text>
                </li>
                <li>
                    <Text size={400}>
                        ... and many more.
                    </Text>
                </li>
            </ul>

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
