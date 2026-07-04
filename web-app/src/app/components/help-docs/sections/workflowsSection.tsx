import {HelpDocSectionInput} from "../helpDocsRegistry";
import {workflowOverviewArticle} from "./articles/workflowOverviewArticle";
import {triggerEventsArticle} from "./articles/triggerEventsArticle";
import {buildingAWorkflowArticle} from "./articles/buildingAWorkflowArticle";
import {workflowApplicabilityArticle} from "./articles/workflowApplicabilityArticle";
import {stepTypesArticle} from "./articles/stepTypesArticle";
import {assigneesAndPortabilityArticle} from "./articles/assigneesAndPortabilityArticle";
import {slaEscalationsArticle} from "./articles/slaEscalationsArticle";
import {platformTemplatesArticle} from "./articles/platformTemplatesArticle";
import {workflowActivityMonitoringArticle} from "./articles/workflowActivityMonitoringArticle";
import {workflowOrgSettingsArticle} from "./articles/workflowOrgSettingsArticle";
import {recipientWorkflowsArticle} from "./articles/recipientWorkflowsArticle";
import {workflowDiagramPreviewArticle} from "./articles/workflowDiagramPreviewArticle";

export const workflowsSection: HelpDocSectionInput = {
    id: "workflows",
    title: "Workflows",
    articles: [
        {id: "workflow-overview",           title: "Workflow overview",                  content: workflowOverviewArticle},
        {id: "trigger-events",              title: "Trigger events",                     content: triggerEventsArticle},
        {id: "building-a-workflow",         title: "Building a workflow",                content: buildingAWorkflowArticle},
        {id: "workflow-applicability",      title: "Field-based applicability",          content: workflowApplicabilityArticle},
        {id: "step-types",                  title: "Step types explained",               content: stepTypesArticle},
        {id: "workflow-diagram-preview",    title: "Workflow diagrams and preview",      content: workflowDiagramPreviewArticle},
        {id: "assignees-and-portability",   title: "Assignees and portability",          content: assigneesAndPortabilityArticle},
        {id: "sla-escalations-reminders",   title: "SLA, escalations, and reminders",    content: slaEscalationsArticle},
        {id: "platform-templates",          title: "Platform templates",                 content: platformTemplatesArticle},
        {id: "workflow-activity-monitoring", title: "Workflow activity and monitoring",   content: workflowActivityMonitoringArticle},
        {id: "workflow-org-settings",       title: "Organization workflow settings",      content: workflowOrgSettingsArticle},
        {id: "recipient-workflows",         title: "Recipient-side workflows",           content: recipientWorkflowsArticle},
    ],
};
