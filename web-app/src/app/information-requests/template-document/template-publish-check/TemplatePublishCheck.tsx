import {Button, Text} from "@fluentui/react-components";
import {TemplateDraftProblem, TemplateDraftTarget} from "../templateDraftRules.ts";
import {useTemplatePublishCheckStyles} from "./TemplatePublishCheckStyles.tsx";

interface TemplatePublishCheckProps
{
    id: string;
    title: string;
    problems: TemplateDraftProblem[];
    readyText: string;
    onShow: (target: TemplateDraftTarget) => void;
}

const TemplatePublishCheck = ({id, title, problems, readyText, onShow}: TemplatePublishCheckProps) =>
{
    const styles = useTemplatePublishCheckStyles();

    return (
        <section id={id}
                 aria-label={title}
                 className={styles.check}>
            <Text id={`${id}-title`}
                  weight={"semibold"}>
                {problems.length === 0 ? title : `${title}: ${problems.length} to resolve`}
            </Text>
            {problems.length === 0 && (
                <Text id={`${id}-ready`}
                      className={styles.muted}>
                    {readyText}
                </Text>
            )}
            {problems.length > 0 && (
                <ul id={`${id}-list`}
                    className={styles.list}>
                    {problems.map(problem => (
                        <li key={problem.key}
                            id={`${id}-${problem.key}`}>
                            <Button id={`${id}-${problem.key}-show`}
                                    appearance={"transparent"}
                                    shape={"circular"}
                                    size={"small"}
                                    className={styles.problem}
                                    onClick={() => onShow(problem.target)}>
                                {problem.message}
                            </Button>
                        </li>
                    ))}
                </ul>
            )}
        </section>
    );
};

export default TemplatePublishCheck;
