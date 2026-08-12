import {Text, Title1, Title3} from "@fluentui/react-components";
import {Breadcrumbs} from "../../shared/Breadcrumbs.tsx";
import {LinkButton} from "../../shared/LinkButton.tsx";
import {PageShell} from "../../shared/PageShell.tsx";
import {useLegalDocumentPageStyles} from "./LegalDocumentPageStyles.tsx";

export type LegalSection = {
    heading: string;
    paragraphs: readonly string[];
};

export type LegalDocument = {
    title: string;
    pageId: string;
    summary: string;
    sections: readonly LegalSection[];
};

type LegalDocumentPageProps = {
    document: LegalDocument;
};

export function LegalDocumentPage({document}: LegalDocumentPageProps)
{
    const styles = useLegalDocumentPageStyles();

    return (
        <PageShell>
            <Breadcrumbs trail={[{label: "Home", to: "/"}, {label: document.title}]}/>
            <main
                id={document.pageId}
                className={styles.wrapper}
                aria-labelledby={`${document.pageId}-title`}
            >
                <header
                    id={`${document.pageId}-header`}
                    className={styles.header}
                >
                    <Text
                        id={`${document.pageId}-label`}
                        className={styles.label}
                    >
                        Legal
                    </Text>
                    <Title1
                        id={`${document.pageId}-title`}
                        as="h1"
                        className={styles.title}
                    >
                        {document.title}
                    </Title1>
                    <Text
                        id={`${document.pageId}-effective-date`}
                        className={styles.effectiveDate}
                    >
                        Effective date: 12 August 2026
                    </Text>
                    <Text
                        id={`${document.pageId}-summary`}
                        size={500}
                        className={styles.summary}
                    >
                        {document.summary}
                    </Text>
                </header>
                <div
                    id={`${document.pageId}-content`}
                    className={styles.content}
                >
                    {document.sections.map((section, sectionIndex) => (
                        <section
                            id={`${document.pageId}-section-${sectionIndex + 1}`}
                            className={styles.section}
                            key={section.heading}
                        >
                            <Title3
                                id={`${document.pageId}-section-${sectionIndex + 1}-title`}
                                as="h2"
                                className={styles.sectionTitle}
                            >
                                {section.heading}
                            </Title3>
                            {section.paragraphs.map((paragraph, paragraphIndex) => (
                                <Text
                                    id={`${document.pageId}-section-${sectionIndex + 1}-paragraph-${paragraphIndex + 1}`}
                                    as="p"
                                    className={styles.paragraph}
                                    key={paragraph}
                                >
                                    {paragraph}
                                </Text>
                            ))}
                        </section>
                    ))}
                </div>
                <div
                    id={`${document.pageId}-actions`}
                    className={styles.actions}
                >
                    <LinkButton
                        id={`${document.pageId}-home-button`}
                        to="/"
                        appearance="primary"
                        shape="circular"
                    >
                        Back home
                    </LinkButton>
                    <LinkButton
                        id={`${document.pageId}-contact-button`}
                        to="/#home-contact-info"
                        appearance="secondary"
                        shape="circular"
                    >
                        Contact DocuHyphen
                    </LinkButton>
                </div>
            </main>
        </PageShell>
    );
}
