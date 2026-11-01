import {HelpDocSectionInput} from "../helpDocsRegistry";

export const startHereSection: HelpDocSectionInput = {
    id: "start-here",
    title: "Start here",
    articles: [
        {
            id: "help-starter-guide",
            title: "Help starter guide",
            content: (
                <>
                    <p id={"start-here-section-p-1"}>
                        Welcome to DocuHyphen Help. If you are not sure where to begin,
                        use this recommended reading order.
                    </p>
                    <p id={"start-here-section-p-2"}>
                        Press <b id={"start-here-section-b-1"}>F1</b> to toggle this help panel. On some Mac keyboards,
                        press <b id={"start-here-section-b-2"}>Fn</b> + <b id={"start-here-section-b-3"}>F1</b>.
                    </p>

                    <h3 id={"start-here-section-h3-1"}>Recommended reading order</h3>
                    <ol id={"start-here-section-ol-1"}>
                        <li id={"start-here-section-li-1"}>
                            <a id={"start-here-section-a-1"}
                               href="#"
                               data-help-article="idp-setup-guide"><b id={"start-here-section-b-4"}>IdP setup guide</b></a> - configure sign-in and organization access.
                        </li>
                        <li id={"start-here-section-li-2"}>
                            <a id={"start-here-section-a-2"}
                               href="#"
                               data-help-article="role-permission-matrix"><b id={"start-here-section-b-5"}>Role & Permission Matrix</b></a> - assign least-privilege roles.
                        </li>
                        <li id={"start-here-section-li-3"}>
                            <a id={"start-here-section-a-3"}
                               href="#"
                               data-help-article="exchange-lifecycle"><b id={"start-here-section-b-6"}>Exchange Lifecycle</b></a> - standardize the end-to-end Exchange flow.
                        </li>
                        <li id={"start-here-section-li-4"}>
                            <a id={"start-here-section-a-4"}
                               href="#"
                               data-help-article="workflow-overview"><b id={"start-here-section-b-7"}>Workflow overview</b></a> - automate and gate lifecycle transitions with configurable workflows.
                        </li>
                    </ol>

                    <h3 id={"start-here-section-h3-2"}>Registering your organization</h3>
                    <p id={"start-here-section-p-3"}>
                        If you do not belong to an organization yet, open <b id={"start-here-section-b-8"}>Settings</b> and select
                        <b id={"start-here-section-b-9"}> Register your org</b> under <b id={"start-here-section-b-10"}>Organization</b>. The registration form shows on
                        the right, like any other settings page. After you register, the same entry reads
                        <b id={"start-here-section-b-11"}> Administration</b> and shows your organization&apos;s review status.
                    </p>

                    <h3 id={"start-here-section-h3-3"}>Who should read what</h3>
                    <ul id={"start-here-section-ul-1"}>
                        <li id={"start-here-section-li-5"}><b id={"start-here-section-b-12"}>Organization Admin:</b> Start with IdP and Role & Permission Matrix, then read the full Workflows section and Variables & Sequences overview.</li>
                        <li id={"start-here-section-li-6"}><b id={"start-here-section-b-13"}>Operations/Managers:</b> Start with Exchange Lifecycle and Workflow overview.</li>
                        <li id={"start-here-section-li-7"}><b id={"start-here-section-b-14"}>New team members:</b> Read all three core articles once for shared context.</li>
                    </ul>

                    <h3 id={"start-here-section-h3-4"}>Workflow quick links</h3>
                    <ul id={"start-here-section-ul-2"}>
                        <li id={"start-here-section-li-8"}>
                            <a id={"start-here-section-a-5"}
                               href="#"
                               data-help-article="building-a-workflow"><b id={"start-here-section-b-15"}>Building a workflow</b></a> - create your first workflow definition step by step.
                        </li>
                        <li id={"start-here-section-li-9"}>
                            <a id={"start-here-section-a-6"}
                               href="#"
                               data-help-article="step-types"><b id={"start-here-section-b-16"}>Step types explained</b></a> - APPROVAL, NOTIFICATION, CONDITION, and ACTION.
                        </li>
                        <li id={"start-here-section-li-10"}>
                            <a id={"start-here-section-a-7"}
                               href="#"
                               data-help-article="platform-templates"><b id={"start-here-section-b-17"}>Platform templates</b></a> - clone ready-made workflows into your personal collection.
                        </li>
                        <li id={"start-here-section-li-11"}>
                            <a id={"start-here-section-a-8"}
                               href="#"
                               data-help-article="workflow-activity-monitoring"><b id={"start-here-section-b-18"}>Workflow activity and monitoring</b></a> - track running instances and review audit timelines.
                        </li>
                    </ul>

                    <h3 id={"start-here-section-h3-5"}>Blueprints quick links</h3>
                    <ul id={"start-here-section-ul-3"}>
                        <li id={"start-here-section-li-12"}>
                            <a id={"start-here-section-a-9"}
                               href="#"
                               data-help-article="blueprint-overview"><b id={"start-here-section-b-19"}>Blueprints overview</b></a> - what blueprints are and when to use them.
                        </li>
                        <li id={"start-here-section-li-13"}>
                            <a id={"start-here-section-a-10"}
                               href="#"
                               data-help-article="using-blueprints"><b id={"start-here-section-b-20"}>Starting an Exchange from a blueprint</b></a> - pick a blueprint and pre-fill an Exchange in seconds.
                        </li>
                        <li id={"start-here-section-li-14"}>
                            <a id={"start-here-section-a-11"}
                               href="#"
                               data-help-article="managing-blueprints"><b id={"start-here-section-b-21"}>Creating and managing blueprints</b></a> - build, edit, and organise your personal blueprints.
                        </li>
                        <li id={"start-here-section-li-15"}>
                            <a id={"start-here-section-a-12"}
                               href="#"
                               data-help-article="org-blueprints"><b id={"start-here-section-b-22"}>Organization blueprints</b></a> - publish shared blueprints across your team.
                        </li>
                    </ul>

                    <h3 id={"start-here-section-h3-6"}>Communications quick links</h3>
                    <ul id={"start-here-section-ul-4"}>
                        <li id={"start-here-section-li-16"}>
                            <a id={"start-here-section-a-13"}
                               href="#"
                               data-help-article="communications-overview">
                                <b id={"start-here-section-b-23"}>Communications overview</b>
                            </a> - reusable message definitions with variable tokens for workflow notifications.
                        </li>
                        <li id={"start-here-section-li-17"}>
                            <a id={"start-here-section-a-14"}
                               href="#"
                               data-help-article="managing-communications">
                                <b id={"start-here-section-b-24"}>Managing communications</b>
                            </a> - create, edit, publish, and organise communications.
                        </li>
                        <li id={"start-here-section-li-18"}>
                            <a id={"start-here-section-a-15"}
                               href="#"
                               data-help-article="communications-in-workflows">
                                <b id={"start-here-section-b-25"}>Using communications in workflows</b>
                            </a> - attach a communication to a NOTIFICATION step.
                        </li>
                    </ul>

                    <h3 id={"start-here-section-h3-7"}>Variables & Sequences quick links</h3>
                    <ul id={"start-here-section-ul-5"}>
                        <li id={"start-here-section-li-19"}>
                            <a id={"start-here-section-a-16"}
                               href="#"
                               data-help-article="variables-overview">
                                <b id={"start-here-section-b-26"}>Variables & Sequences overview</b>
                            </a> - what tokens are and how they work.
                        </li>
                        <li id={"start-here-section-li-20"}>
                            <a id={"start-here-section-a-17"}
                               href="#"
                               data-help-article="using-variable-tokens">
                                <b id={"start-here-section-b-27"}>Using variable tokens</b>
                            </a> - insert and preview tokens in blueprints and Exchanges.
                        </li>
                        <li id={"start-here-section-li-21"}>
                            <a id={"start-here-section-a-18"}
                               href="#"
                               data-help-article="managing-sequences">
                                <b id={"start-here-section-b-28"}>Managing sequences</b>
                            </a> - create auto-incrementing counters for Exchange names.
                        </li>
                    </ul>
                </>
            ),
        },
    ],
};
