import React from 'react';
import { useParams } from 'react-router-dom';

const SharingSession: React.FC = () => {
    const { id } = useParams<{ id: string }>();

    return (
        <div>
            <h1>Sharing Session</h1>
            <p>Session ID: {id}</p>
            {/* Add more details about the sharing session here */}
        </div>
    );
};

export default SharingSession;