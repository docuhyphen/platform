import React from 'react';

const Landing: React.FC = () =>
{
    return (
        <div>
            <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                <h1>Welcome to the Landing Page</h1>
            </div>
            <p>You have successfully signed in!</p>
        </div>
    );
};

export default Landing;