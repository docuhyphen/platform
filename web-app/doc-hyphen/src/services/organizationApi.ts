
import {
    AppUser,
    AppUserRole,
    Organization,
    Person,
    PersonIDType
} from "../app/models/models.tsx";

// Mock data for development use
const pairedOrganizations: Organization[] = [
    {
        id: "a1b2c3d4-e5f6-47a1-8901-23456789abcd",
        name: "Acme Corp",
        isActive: true,
        verificationComplete: true,
        createdDate: "2023-06-15",
        registrationNumber: "ACM12345",
        appUsers: [],
        contactDetails: {
            id: "cd-a1b2c3d4",
            createdDate: "2023-06-15",
            email: "contact@acme.com",
            phoneNumber: "+1234567890"
        }
    },
    {
        id: "b2c3d4e5-f6a7-48b2-9012-3456789abcde",
        name: "TechSolutions Inc",
        isActive: true,
        verificationComplete: true,
        createdDate: "2023-07-22",
        registrationNumber: "TECH54321",
        appUsers: [],
        contactDetails: {
            id: "cd-b2c3d4e5",
            createdDate: "2023-07-22",
            email: "contact@techsolutions.com",
            phoneNumber: "+2345678901"
        }
    },
    {
        id: "c3d4e5f6-a7b8-49c3-0123-456789abcdef",
        name: "Global Innovations",
        isActive: true,
        verificationComplete: true,
        createdDate: "2023-08-05",
        registrationNumber: "GI78901",
        appUsers: [],
        contactDetails: {
            id: "cd-c3d4e5f6",
            createdDate: "2023-08-05",
            email: "contact@globalinnovations.org",
            phoneNumber: "+3456789012"
        }
    },
    {
        id: "d4e5f6a7-b8c9-40d4-1234-56789abcdef0",
        name: "DataSystems Ltd",
        isActive: false,
        verificationComplete: false,
        createdDate: "2023-09-10",
        registrationNumber: "DSL98765",
        appUsers: [],
        contactDetails: {
            id: "cd-d4e5f6a7",
            createdDate: "2023-09-10",
            email: "contact@datasystems.co",
            phoneNumber: "+4567890123"
        }
    },
    {
        id: "e5f6a7b8-c9d0-41e5-2345-6789abcdef01",
        name: "Quantum Research",
        isActive: true,
        verificationComplete: true,
        createdDate: "2023-10-01",
        registrationNumber: "QR45678",
        appUsers: [],
        contactDetails: {
            id: "cd-e5f6a7b8",
            createdDate: "2023-10-01",
            email: "contact@quantumresearch.edu",
            phoneNumber: "+5678901234"
        }
    },

    {
        id: "f6a7b8c9-d0e1-42f6-3456-789abcdef012",
        name: "MegaCorp International",
        isActive: true,
        verificationComplete: true,
        createdDate: "2022-11-11",
        registrationNumber: "MCINT2022",
        appUsers: [],
        contactDetails: {
            id: "cd-f6a7b8c9",
            createdDate: "2022-11-11",
            email: "info@megacorp.com",
            phoneNumber: "+6012345678"
        }
    },
    {
        id: "a8b9c0d1-e2f3-43g7-4567-890abcdef123",
        name: "Pinnacle Technologies",
        isActive: true,
        verificationComplete: true,
        createdDate: "2023-01-20",
        registrationNumber: "PINTEC901",
        appUsers: [],
        contactDetails: {
            id: "cd-a8b9c0d1",
            createdDate: "2023-01-20",
            email: "support@pinnacletech.io",
            phoneNumber: "+7012345678"
        }
    },
    {
        id: "b9c0d1e2-f3g4-44h8-5678-901abcdef234",
        name: "NovaGroup Holdings",
        isActive: true,
        verificationComplete: true,
        createdDate: "2023-03-12",
        registrationNumber: "NOVAGROUP456",
        appUsers: [],
        contactDetails: {
            id: "cd-b9c0d1e2",
            createdDate: "2023-03-12",
            email: "hello@novagroup.com",
            phoneNumber: "+8012345678"
        }
    },
    {
        id: "c0d1e2f3-g4h5-45i9-6789-012abcdef345",
        name: "InfiniTech Global",
        isActive: true,
        verificationComplete: true,
        createdDate: "2022-12-01",
        registrationNumber: "ITG3210",
        appUsers: [],
        contactDetails: {
            id: "cd-c0d1e2f3",
            createdDate: "2022-12-01",
            email: "admin@infinitech.com",
            phoneNumber: "+9012345678"
        }
    },
    {
        id: "d1e2f3g4-h5i6-46j0-7890-123abcdef456",
        name: "Helix Dynamics",
        isActive: false,
        verificationComplete: false,
        createdDate: "2023-02-28",
        registrationNumber: "HELIX987",
        appUsers: [],
        contactDetails: {
            id: "cd-d1e2f3g4",
            createdDate: "2023-02-28",
            email: "contact@helixdynamics.ai",
            phoneNumber: "+9988776655"
        }
    }
];

const createPerson = (id: string, firstName: string, lastName: string, email: string): Person => ({
    id: `person-${id}`,
    createdDate: new Date().toISOString(),
    firstName,
    lastName,
    identificationNumber: "",
    personIDType: PersonIDType.ID_NUMBER,
    contactDetails: {
        id: `contact-${id}`,
        createdDate: new Date().toISOString(),
        email,
        phoneNumber: "",
    }
});

const orgUsers: AppUser[] = [
    {
        id: "u1d2e3f4-g5h6-42i1-3456-789abcdef012",
        email: "john.smith@acme.com",
        isActive: true,
        emailVerificationComplete: true,
        createdDate: "2023-06-16",
        signInAttempts: 0,
        mfaType: "EMAIL",
        isTemporary: false,
        role: AppUserRole.ORG_MEMBER,
        person: createPerson("js", "John", "Smith", "john.smith@acme.com")
    },
    {
        id: "v2e3f4g5-h6i7-43j2-4567-89abcdef0123",
        email: "sarah.jones@acme.com",
        isActive: true,
        emailVerificationComplete: true,
        createdDate: "2023-06-17",
        signInAttempts: 0,
        mfaType: "EMAIL",
        isTemporary: false,
        role: AppUserRole.ORG_MEMBER,
        person: createPerson("sj", "Sarah", "Jones", "sarah.jones@acme.com")
    },
    // Additional users...
    {
        id: "w3f4g5h6-i7j8-44k3-5678-9abcdef01234",
        email: "michael.brown@techsolutions.com",
        isActive: true,
        emailVerificationComplete: true,
        createdDate: "2023-07-23",
        signInAttempts: 0,
        mfaType: "EMAIL",
        isTemporary: false,
        role: AppUserRole.ORG_ADMIN,
        person: createPerson("mb", "Michael", "Brown", "michael.brown@techsolutions.com")
    },
    {
        id: "x4g5h6i7-j8k9-45l4-6789-abcdef012345",
        email: "emily.white@globalinnovations.org",
        isActive: true,
        emailVerificationComplete: true,
        createdDate: "2023-08-06",
        signInAttempts: 0,
        mfaType: "EMAIL",
        isTemporary: false,
        role: AppUserRole.ORG_MEMBER,
        person: createPerson("ew", "Emily", "White", "emily.white@globalinnovations.org")
    },
    {
        id: "y5h6i7j8-k9l0-46m5-7890-bcdef0123456",
        email: "david.miller@quantumresearch.edu",
        isActive: true,
        emailVerificationComplete: true,
        createdDate: "2023-10-02",
        signInAttempts: 0,
        mfaType: "EMAIL",
        isTemporary: false,
        role: AppUserRole.ORG_MEMBER,
        person: createPerson("dm", "David", "Miller", "david.miller@quantumresearch.edu")
    }
];

// Associate users with their organizations
orgUsers.forEach(user => {
    let organizationId;
    if (user.email.includes("acme.com")) {
        organizationId = "a1b2c3d4-e5f6-47a1-8901-23456789abcd";
    } else if (user.email.includes("techsolutions.com")) {
        organizationId = "b2c3d4e5-f6a7-48b2-9012-3456789abcde";
    } else if (user.email.includes("globalinnovations.org")) {
        organizationId = "c3d4e5f6-a7b8-49c3-0123-456789abcdef";
    } else if (user.email.includes("quantumresearch.edu")) {
        organizationId = "e5f6a7b8-c9d0-41e5-2345-6789abcdef01";
    }

    const org = pairedOrganizations.find(o => o.id === organizationId);
    if (org) {
        org.appUsers.push(user);
    }
});

// Define interface for organization groups
export interface OrganizationGroupBasicDto
{
    id: string;
    name: string;
    description: string;
    organizationId: string;
    memberCount: number;
}

const orgGroups: OrganizationGroupBasicDto[] = [
    {
        id: "g1a2b3c4-d5e6-47f7-8901-234567890abc",
        name: "Marketing Team",
        description: "Marketing department",
        organizationId: "a1b2c3d4-e5f6-47a1-8901-23456789abcd", // Acme Corp
        memberCount: 12
    },
    {
        id: "h2b3c4d5-e6f7-48g8-9012-3456789abcde",
        name: "Development Team",
        description: "Software development team",
        organizationId: "a1b2c3d4-e5f6-47a1-8901-23456789abcd", // Acme Corp
        memberCount: 8
    },
    // Additional groups...
    {
        id: "i3c4d5e6-f7g8-49h9-0123-456789abcdef",
        name: "Research Group",
        description: "Research and development",
        organizationId: "b2c3d4e5-f6a7-48b2-9012-3456789abcde", // TechSolutions Inc
        memberCount: 15
    },
    {
        id: "j4d5e6f7-g8h9-50i0-1234-56789abcdef0",
        name: "Finance Department",
        description: "Finance and accounting",
        organizationId: "c3d4e5f6-a7b8-49c3-0123-456789abcdef", // Global Innovations
        memberCount: 7
    },
    {
        id: "k5e6f7g8-h9i0-51j1-2345-6789abcdef01",
        name: "Executive Team",
        description: "Executive leadership",
        organizationId: "e5f6a7b8-c9d0-41e5-2345-6789abcdef01", // Quantum Research
        memberCount: 5
    }
];

// Implement API service functions that simulate API calls
export const fetchPairedOrganizations = async (): Promise<Organization[]> => {
    // Simulate network delay
    await new Promise(resolve => setTimeout(resolve, 300));
    return pairedOrganizations;
};

export const fetchOrganizationUsers = async (organizationId: string): Promise<AppUser[]> => {
    // Simulate network delay
    await new Promise(resolve => setTimeout(resolve, 200));
    const org = pairedOrganizations.find(o => o.id === organizationId);
    return org?.appUsers || [];
};

export const fetchOrganizationGroups = async (organizationId: string): Promise<OrganizationGroupBasicDto[]> => {
    // Simulate network delay
    await new Promise(resolve => setTimeout(resolve, 200));
    return orgGroups.filter(group => group.organizationId === organizationId);
};

export const fetchMyOrganizationUsers = async (): Promise<AppUser[]> => {
    // This would be replaced with an actual API call to get current org users
    await new Promise(resolve => setTimeout(resolve, 300));
    return orgUsers.filter(user => user.email.includes("acme.com"));
};

export const fetchMyOrganizationGroups = async (): Promise<OrganizationGroupBasicDto[]> => {
    // This would be replaced with an actual API call to get current org groups
    await new Promise(resolve => setTimeout(resolve, 250));
    return orgGroups.filter(group =>
        group.organizationId === "a1b2c3d4-e5f6-47a1-8901-23456789abcd"
    );
};