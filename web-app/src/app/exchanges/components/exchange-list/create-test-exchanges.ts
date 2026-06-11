// script.js - Create 100 exchanges for pagination testing

// Configuration
const BASE_URL = 'http://localhost:8080';
const AUTH_TOKEN = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJlMTU4MTQ1ZC1lMzI2LTQ5NmYtYjNlZS1mMGQ1NWE0YzZhMzkiLCJlbWFpbCI6InRlc3QxQGRvYy1oeXBoZW4uY29tIiwiaWF0IjoxNzQzMTkxOTE5LCJleHAiOjE3NDMxOTU1MTl9.7FLl9AKhB-A_1bGeb1_QnuwKICbj6MaZfDyz4NsbFRo';
const TOTAL_SESSIONS = 100;

// Function to create a single exchange
async function createExchange(index)
{
    const exchangeData = {
        name: `Pagination Test ${index}`,
        description: `Test exchange ${index} for pagination testing`,
        recipientEmail: "test2@docuhyphen.com",
        initialShareMessage: `Testing pagination with exchange ${index}`,
        exchangeDocuments: [
            {
                title: `Test document for pagination ${index}`,
                restrictType: false
            }
        ],
        requestRecipientSignIn: false,
        allowDocumentAddition: false,
        allowDocumentDeletion: false,
        allowDocumentDownload: true,
        allowDocumentUpdate: false,
        allowDocumentUpload: false
    };

    try
    {
        const response = await fetch(`${BASE_URL}/exchanges`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${AUTH_TOKEN}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(exchangeData)
        });

        if (!response.ok)
        {
            const errorText = await response.text();
            throw new Error(`Failed to create exchange ${index}: HTTP ${response.status} - ${errorText}`);
        }

        const data = await response.json();
        console.log(`Created exchange ${index}: ${data.id}`);
        return data;
    }
    catch (error)
    {
        console.error(`Error creating exchange ${index}:`, error.message);
        return null;
    }
}

// Function to add a delay between requests to prevent overwhelming the server
function delay(ms)
{
    return new Promise(resolve => setTimeout(resolve, ms));
}

// Main function to create all exchanges
export const createAllExchanges = async () =>
{
    console.log(`Starting creation of ${TOTAL_SESSIONS} exchanges...`);

    let successCount = 0;
    let failCount = 0;

    for (let i = 15; i <= TOTAL_SESSIONS; i++)
    {
        console.log(`Creating exchange ${i} of ${TOTAL_SESSIONS}...`);
        const result = await createExchange(i);

        if (result)
        {
            successCount++;
        }
        else
        {
            failCount++;
        }

        // Add a small delay between requests to avoid overwhelming the server
        await delay(300);
    }

    console.log(`\nComplete! Created ${successCount} exchanges successfully.`);
    if (failCount > 0)
    {
        console.log(`Failed to create ${failCount} exchanges.`);
    }
}
