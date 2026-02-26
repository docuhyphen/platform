import {
    Button,
    Title2,
    Text,
    Card,
    CardHeader, Badge, Divider, LargeTitle, mergeClasses,
} from "@fluentui/react-components";
import AppLogo from "./app-logo/AppLogo.tsx";
import {appStyles} from "./AppStyles.tsx";

export default function App()
{
    const styles = appStyles();

    return (
        <main className={styles.page}>
            <section className={styles.mainMenuContainer}>
                <section className={styles.mainMenu}>
                    <AppLogo/>
                    <div>
                        <Button appearance="subtle"
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
            <section className={mergeClasses(styles.sectionContainerWrapper, styles.section1ContainerWrapper)}>
                <section className={styles.section1Container}>
                    <LargeTitle align="center"
                                className={styles.section1Title}>
                        Welcome to{" "}
                        <span className={styles.noWrap}>
                            DocuHyphen
                        </span>
                        , secure document sharing for your sensitive business data
                    </LargeTitle>
                    <Text size={400} align={"center"}>
                        Request, send, and track sensitive documents with encryption,
                        verification, and full audit trails.
                    </Text>

                    <div className={styles.section1Actions}>
                        <Button appearance="primary"
                                as="a"
                                size={"large"}
                                className={styles.btnLong}
                                shape={"circular"}
                                target={"_blank"}
                                href="https://app.docuhyphen.com/sign-up">
                            Try it for free
                        </Button>
                        <Text weight={"semibold"}> OR </Text>
                        <Button as="a"
                                appearance={"outline"}
                                size={"large"}
                                shape={"circular"}>
                            Speak to Sales
                        </Button>
                    </div>
                </section>
            </section>
            <section className={mergeClasses(styles.sectionContainerWrapper, styles.section2ContainerWrapper)}>
                <div className={styles.section2Container}>
                    <Title2 align={"start"}
                            className={styles.sectionTitle}>
                        The Hidden Risks of Everyday Document Sharing
                    </Title2>
                    <div>
                        <Text weight={"medium"}>
                            Traditional tools prioritize convenience over control, leaving your organization
                            exposed.
                        </Text>
                        <Text>
                            <br/>
                            Email and generic file-sharing tools weren’t built for sensitive business data —
                            yet they’re still used for financial records, contracts, ID documents, and confidential
                            reports.
                            That creates serious risk.
                        </Text>
                    </div>
                </div>
            </section>

            <section className={mergeClasses(styles.sectionContainerWrapper, styles.section3ContainerWrapper)}>
                <div className={styles.section3Container}>
                    <div className={mergeClasses(styles.section3Card, styles.section3Card1)}>
                        <Text weight="semibold"
                              className={styles.section3CardTitleContainer}>
                            <Badge size={"large"}
                                   appearance={"filled"}> 1 </Badge>
                            High risk of unauthorized access
                        </Text>
                        <Text>
                            Sensitive documents can be intercepted, forwarded
                            without permission, downloaded to unsecured devices,
                            or stored indefinitely in inboxes and shared drives.
                            Once sent, control is lost.
                        </Text>
                    </div>
                    <div className={mergeClasses(styles.section3Card, styles.section3Card2)}>
                        <Text weight="semibold"
                              className={styles.section3CardTitleContainer}>
                            <Badge size={"large"}
                                   appearance={"filled"}> 2 </Badge>
                            Uncontrolled document distribution
                        </Text>
                        <Text>
                            Links can be forwarded. Emails can be mistyped.
                            Attachments can be duplicated and redistributed.
                            There’s no reliable way to verify the intended
                            recipient or restrict downstream sharing.
                        </Text>
                    </div>
                    <div className={mergeClasses(styles.section3Card, styles.section3Card3)}>
                        <Text weight="semibold"
                              className={styles.section3CardTitleContainer}>
                            <Badge size={"large"}
                                   appearance={"filled"}>
                                3
                            </Badge>
                            No defensible audit trail
                        </Text>
                        <Text>
                            Most tools provide little visibility into who accessed documents,
                            when they were accessed, or what actions were taken.
                            When disputes or investigations arise, proof is often unavailable.
                        </Text>
                    </div>
                    <div className={mergeClasses(styles.section3Card, styles.section3Card4)}>
                        <Text weight="semibold"
                              className={styles.section3CardTitleContainer}>
                            <Badge size={"large"}
                                   appearance={"filled"}>
                                4
                            </Badge>
                            Regulatory exposure (POPIA, GDPR & similar laws)
                        </Text>
                        <Text>
                            Data protection regulations require secure processing, controlled
                            access, and demonstrable accountability. Without encryption, access
                            control, and detailed logging, organizations risk fines, legal exposure,
                            and reputational damage.
                        </Text>
                    </div>
                </div>
            </section>

            <Divider></Divider>

            <section className={mergeClasses(styles.sectionContainerWrapper, styles.section4ContainerWrapper)}>
                <section className={styles.section4Container}>
                    <Title2 className={styles.sectionTitle}>
                        Secure, Controlled & Compliant Document Exchange
                    </Title2>
                    <Text size={500}>
                        A purpose-built platform designed specifically for exchanging
                        sensitive business and customer documents, securely, transparently,
                        and compliantly.
                    </Text>
                </section>
            </section>
            <section className={mergeClasses(styles.sectionContainerWrapper, styles.section5ContainerWrapper)}>
                <div className={styles.section5Container}>
                    <Card appearance="filled-alternative">
                        <CardHeader header={
                            <Badge size={"extra-large"}
                                   appearance={"tint"}>
                                <Text weight={"bold"}> Structured Document Requests </Text>
                            </Badge>}/>
                        <Text>
                            Create controlled share sessions to request documents
                            from customers or partners, with defined access, expiry controls,
                            and full visibility.
                        </Text>
                    </Card>
                    <Card appearance="filled-alternative">
                        <CardHeader
                            header={
                                <Badge size={"extra-large"}
                                       appearance={"tint"}>
                                    <Text weight={"bold"}> Secure Document Delivery </Text>
                                </Badge>}/>
                        <Text>
                            Deliver financial, legal, and confidential documents
                            without email attachments or public links,
                            ensuring only authorized recipients can access them.
                        </Text>
                    </Card>
                    <Card appearance="filled-alternative">
                        <CardHeader
                            header={
                                <Badge size={"extra-large"}
                                       appearance={"tint"}>
                                    <Text weight={"bold"}> Encryption by Default </Text>
                                </Badge>}/>
                        <Text>
                            Files are encrypted during upload, storage, and download,
                            with optional end-to-end encryption for maximum confidentiality.
                        </Text>
                    </Card>
                    <Card appearance="filled-alternative">
                        <CardHeader header={
                            <Badge size={"extra-large"}
                                   appearance={"tint"}>
                                <Text weight={"bold"}> Full Audit & Compliance Logging </Text>
                            </Badge>}/>
                        <Text>
                            Every upload, view, download, and action is recorded,
                            giving you defensible audit trails and supporting regulatory compliance.
                        </Text>
                    </Card>

                </div>
            </section>

            <section className={mergeClasses(styles.sectionContainerWrapper, styles.section6ContainerWrapper)}>
                <section className={styles.section6Container}>
                    <Title2 align="center">
                        Who This Platform Is Built For...
                    </Title2>

                    <Text className={styles.whoForSubtitle} size={500}>
                        Built for organizations that handle sensitive documents
                        and require security, compliance, and accountability.
                    </Text>

                    <section className={styles.whoForGrid}>

                        <Card className={styles.whoForCard}>
                            <CardHeader header={
                                <Badge size={"extra-large"}
                                       appearance={"tint"}>
                                    <Text weight={"bold"}> Financial & Professional Services</Text>
                                </Badge>}/>
                            <ul className={styles.whoForList}>
                                <li>
                                    <Text weight="semibold">
                                        Accounting & audit firms
                                    </Text>
                                    <Text size={300}>
                                        Secure exchange of tax records, financial statements,
                                        and supporting documents.
                                    </Text>
                                </li>
                                <li>
                                    <Text weight="semibold">
                                        Financial advisors & wealth managers
                                    </Text>
                                    <Text size={300}>
                                        Protect client identity and financial data while maintaining
                                        full audit trails.
                                    </Text>
                                </li>
                                <li>
                                    <Text weight="semibold">
                                        Banks & lending institutions
                                    </Text>
                                    <Text size={300}>
                                        Collect and share KYC, income verification, and compliance
                                        documents securely.
                                    </Text>
                                </li>
                            </ul>
                        </Card>

                        <Card className={styles.whoForCard}>
                            <CardHeader header={
                                <Badge size={"extra-large"}
                                       appearance={"tint"}>
                                    <Text weight={"bold"}> Insurance & Risk Management </Text>
                                </Badge>}/>
                            <ul className={styles.whoForList}>
                                <li>
                                    <Text weight="semibold">
                                        Insurance companies
                                    </Text>
                                    <Text size={300}>
                                        Secure handling of policy documents, claims, and customer identity records.
                                    </Text>
                                </li>
                                <li>
                                    <Text weight="semibold">
                                        Insurance brokers & underwriters
                                    </Text>
                                    <Text size={300}>
                                        Controlled document sharing between clients, insurers, and assessors.
                                    </Text>
                                </li>
                                <li>
                                    <Text weight="semibold">
                                        Claims management firms
                                    </Text>
                                    <Text size={300}>
                                        Track document submissions with visibility and accountability.
                                    </Text>
                                </li>
                            </ul>
                        </Card>

                        {/* Legal & Regulatory */}
                        <Card className={styles.whoForCard}>
                            <CardHeader
                                header={
                                    <Badge size={"extra-large"}
                                           appearance={"tint"}>
                                        <Text weight={"bold"}> Legal & Regulatory</Text>
                                    </Badge>}/>
                            <ul className={styles.whoForList}>
                                <li>
                                    <Text weight="semibold">
                                        Law firms & legal practices
                                    </Text>
                                    <Text size={300}>
                                        Exchange contracts, affidavits, and sensitive
                                        legal documents with clients.
                                    </Text>
                                </li>
                                <li>
                                    <Text weight="semibold">
                                        Compliance & risk consultancies
                                    </Text>
                                    <Text size={300}>
                                        Maintain verifiable audit trails for regulated workflows.
                                    </Text>
                                </li>
                                <li>
                                    <Text weight="semibold">
                                        Corporate secretarial services
                                    </Text>
                                    <Text size={300}>
                                        Secure storage and exchange of statutory and company records.
                                    </Text>
                                </li>
                            </ul>
                        </Card>

                        <Card className={styles.whoForCard}>
                            <CardHeader header={
                                <Badge size={"extra-large"}
                                       appearance={"tint"}>
                                    <Text weight={"bold"}> Corporate & Enterprise Operations</Text>
                                </Badge>}/>
                            <ul className={styles.whoForList}>
                                <li>
                                    <Text weight="semibold">
                                        Medium to large enterprises
                                    </Text>
                                    <Text size={300}>
                                        Internal and external document sharing with verified parties.
                                    </Text>
                                </li>
                                <li>
                                    <Text weight="semibold">
                                        Procurement & vendor onboarding teams
                                    </Text>
                                    <Text size={300}>
                                        Collect compliance documents from suppliers securely.
                                    </Text>
                                </li>
                                <li>
                                    <Text weight="semibold">
                                        HR & payroll departments
                                    </Text>
                                    <Text size={300}>
                                        Exchange employee identity, tax, and contract documents safely.
                                    </Text>
                                </li>
                            </ul>
                        </Card>
                    </section>
                </section>
            </section>
            <section className={styles.footerCta}>
                <Text weight={"regular"}
                      size={600}>
                    Take Control of Your Sensitive Documents
                </Text>
                <div>
                    <Button appearance="primary"
                            as="a"
                            size={"large"}
                            target={"_blank"}
                            shape={"circular"}
                            className={styles.btnLong}
                            href="https://app.docuhyphen.com/sign-up">
                        Try it for free
                    </Button>
                </div>
            </section>
        </main>
    );
}
