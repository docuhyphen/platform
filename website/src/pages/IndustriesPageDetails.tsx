import {Text, Title3} from "@fluentui/react-components";
import {Checkmark20Filled} from "@fluentui/react-icons";
import type {IndustriesContent} from "./IndustriesPageContent.ts";
import {useIndustriesPageStyles} from "./IndustriesPageStyles.tsx";

interface IndustriesPageDetailsProps
{
    data: IndustriesContent;
}

export function IndustriesPageDetails({data}: IndustriesPageDetailsProps)
{
    const styles = useIndustriesPageStyles();

    return (
        <div
            id="industry-detail-content-grid"
            className={styles.grid2}
        >
            <section
                id="industry-detail-use-cases"
                className={styles.panel}
            >
                <Title3
                    id="industry-detail-use-cases-title"
                    className={styles.panelTitle}
                >
                    Use cases
                </Title3>
                {data.useCases.map((useCase, index) => (
                    <div
                        id={`industry-detail-use-case-${index}`}
                        key={useCase.title}
                        className={index === 0 ? undefined : styles.useCase}
                    >
                        <Text weight="semibold">{useCase.title}</Text><br/>
                        <Text>{useCase.body}</Text>
                    </div>
                ))}
            </section>

            <section
                id="industry-detail-benefits"
                className={styles.panel}
            >
                <Title3
                    id="industry-detail-benefits-title"
                    className={styles.panelTitle}
                >
                    How DocuHyphen helps
                </Title3>
                <ul className={styles.bulletList}>
                    {data.benefits.map((benefit, index) => (
                        <li
                            id={`industry-detail-benefit-${index}`}
                            key={benefit}
                            className={styles.bullet}
                        >
                            <Checkmark20Filled className={styles.bulletIcon}/>
                            {benefit}
                        </li>
                    ))}
                </ul>
            </section>
        </div>
    );
}
