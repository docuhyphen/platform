import {HelpDocSectionInput} from "../helpDocsRegistry";
import {workflowOverviewArticle} from "./articles/workflowOverviewArticle";
import {triggerEventsArticle} from "./articles/triggerEventsArticle";
import {buildingAWorkflowArticle} from "./articles/buildingAWorkflowArticle";
import {stepTypesArticle} from "./articles/stepTypesArticle";
import {assigneesAndPortabilityArticle} from "./articles/assigneesAndPortabilityArticle";
import {slaEscalationsArticle} from "./articles/slaEscalationsArticle";
import {platformTemplatesArticle} from "./articles/platformTemplatesArticle";
import {workflowActivityMonitoringArticle} from "./articles/workflowActivityMonitoringArticle";
import {workflowOrgSettingsArticle} from "./articles/workflowOrgSettingsArticle";

export const workflowsSection: HelpDocSectionInput = {
    id: "workflows",
    title: "Workflows",
    articles: [
        {id: "workflow-overview",           title: "Workflow overview",                  content: workflowOverviewArticle},
        {id: "trigger-events",              title: "Trigger events",                     content: triggerEventsArticle},
        {id: "building-a-workflow",         title: "Building a workflow",                content: buildingAWorkflowArticle},
        {id: "step-types",                  title: "Step types explained",               content: stepTypesArticle},
        {id: "assignees-and-portability",   title: "Assignees and portability",          content: assigneesAndPortabilityArticle},
        {id: "sla-escalations-reminders",   title: "SLA, escalations, and reminders",    content: slaEscalationsArticle},
        {id: "platform-templates",          title: "Platform templates",                 content: platformTemplatesArticle},
        {id: "workflow-activity-monitoring", title: "Workflow activity and monitoring",   content: workflowActivityMonitoringArticle},
        {id: "workflow-org-settings",       title: "Organization workflow settings",      content: workflowOrgSettingsArticle},
    ],
};
