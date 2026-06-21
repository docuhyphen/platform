import React from "react";
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
                    <p>
                        Welcome to DocuHyphen Help. If you are not sure where to begin,
                        use this recommended reading order.
                    </p>

                    <h3>Recommended reading order</h3>
                    <ol>
                        <li>
                            <a href="#"
                               data-help-article="idp-setup-guide"><b>IdP setup guide</b></a> - configure sign-in and organization access.
                        </li>
                        <li>
                            <a href="#"
                               data-help-article="role-permission-matrix"><b>Role & Permission Matrix</b></a> - assign least-privilege roles.
                        </li>
                        <li>
                            <a href="#"
                               data-help-article="exchange-lifecycle"><b>Exchange Lifecycle</b></a> - standardize the end-to-end Exchange flow.
                        </li>
                        <li>
                            <a href="#"
                               data-help-article="workflow-overview"><b>Workflow overview</b></a> - automate and gate lifecycle transitions with configurable workflows.
                        </li>
                    </ol>

                    <h3>Who should read what</h3>
                    <ul>
                        <li><b>Organization Admin:</b> Start with IdP and Role & Permission Matrix, then read the full Workflows section and Variables & Sequences overview.</li>
                        <li><b>Operations/Managers:</b> Start with Exchange Lifecycle and Workflow overview.</li>
                        <li><b>New team members:</b> Read all three core articles once for shared context.</li>
                    </ul>

                    <h3>Workflow quick links</h3>
                    <ul>
                        <li>
                            <a href="#"
                               data-help-article="building-a-workflow"><b>Building a workflow</b></a> - create your first workflow definition step by step.
                        </li>
                        <li>
                            <a href="#"
                               data-help-article="step-types"><b>Step types explained</b></a> - APPROVAL, NOTIFICATION, CONDITION, and ACTION.
                        </li>
                        <li>
                            <a href="#"
                               data-help-article="platform-templates"><b>Platform templates</b></a> - clone ready-made workflows into your organization.
                        </li>
                        <li>
                            <a href="#"
                               data-help-article="workflow-activity-monitoring"><b>Workflow activity and monitoring</b></a> - track running instances and review audit timelines.
                        </li>
                    </ul>

                    <h3>Blueprints quick links</h3>
                    <ul>
                        <li>
                            <a href="#"
                               data-help-article="blueprint-overview"><b>Blueprints overview</b></a> - what blueprints are and when to use them.
                        </li>
                        <li>
                            <a href="#"
                               data-help-article="using-blueprints"><b>Starting an exchange from a blueprint</b></a> - pick a blueprint and pre-fill an exchange in seconds.
                        </li>
                        <li>
                            <a href="#"
                               data-help-article="managing-blueprints"><b>Creating and managing blueprints</b></a> - build, edit, and organise your personal blueprints.
                        </li>
                        <li>
                            <a href="#"
                               data-help-article="org-blueprints"><b>Organization blueprints</b></a> - publish shared blueprints across your team.
                        </li>
                    </ul>

                    <h3>Variables & Sequences quick links</h3>
                    <ul>
                        <li>
                            <a href="#"
                               data-help-article="variables-overview">
                                <b>Variables & Sequences overview</b>
                            </a> - what tokens are and how they work.
                        </li>
                        <li>
                            <a href="#"
                               data-help-article="using-variable-tokens">
                                <b>Using variable tokens</b>
                            </a> - insert and preview tokens in blueprints and exchanges.
                        </li>
                        <li>
                            <a href="#"
                               data-help-article="managing-sequences">
                                <b>Managing sequences</b>
                            </a> - create auto-incrementing counters for exchange names.
                        </li>
                    </ul>
                </>
            ),
        },
    ],
};
