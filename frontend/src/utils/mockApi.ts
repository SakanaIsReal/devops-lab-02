import { Transaction, User, Group, PaymentDetails } from "../types";

export const mockLoginApi = (
  email: string,
  password: string
): Promise<{ user: any; token: string }> => {
  return new Promise((resolve, reject) => {
    // Mock network delay
    setTimeout(() => {
      // Simple validation
      if (email === "user@example.com" && password === "gg") {
        resolve({
          user: {
            id: "1",
            email: email,
            name: "SakanaIsReal",
          },
          token: "fake-jwt-token",
        });
      } else {
        reject(new Error("Invalid email or password"));
      }
    }, 1500);
  });
};

export const mockSignUpApi = (
    firstName: string,
    lastName: string,
    email: string,
    password: string
    ): Promise<User> => {
    return new Promise((resolve) => {
        setTimeout(() => {
            resolve({
                id: "2",
                email: email,
                name: `${firstName} ${lastName}`,
            });
        }, 1500);
    });
};

export const transactions: Transaction[] = [
    {
      id: 1,
      type: "owe",
      name: "Alex",
      amount: 25.0,
      description: "Dinner last night",
      created_date: "2025-09-06T19:00:00Z",
    },
    {
      id: 2,
      type: "owed",
      name: "Mark",
      amount: 15.0,
      description: "Concert tickets",
      created_date: "2025-09-05T12:00:00Z",
    },
    {
      id: 3,
      type: "owed",
      name: "Smolary",
      amount: 45.0,
      description: "Hololive Shipping Cost",
      created_date: "2025-09-04T10:30:00Z",
    },
    {
        id: 4,
        type: "owe",
        name: "John",
        amount: 10.0,
        description: "Coffee",
        created_date: "2025-09-05T09:00:00Z",
    },
    {
        id: 5,
        type: "owed",
        name: "Jane",
        amount: 30.0,
        description: "Movie tickets",
        created_date: "2025-08-27T15:00:00Z",
    },
    {
        id: 6,
        type: "owe",
        name: "Bob",
        amount: 5.0,
        description: "Lunch",
        created_date: "2025-07-01T12:30:00Z",
    },
  ];

export const mockGroups: Group[] = [
  {
    id: "1",
    name: "Trip to Japan",
    participantCount: 5,
    imageUrl: "https://pbs.twimg.com/media/G0G5GtbWsAAHIva?format=jpg&name=medium",
  },
  {
    id: "2",
    name: "Project Hololive",
    participantCount: 3,
    imageUrl: "https://pbs.twimg.com/media/G0G5GtbWsAAHIva?format=jpg&name=medium",
  },
  {
    id: "3",
    name: "Birthday Party",
    participantCount: 10,
    imageUrl: "https://pbs.twimg.com/media/G0G5GtbWsAAHIva?format=jpg&name=medium",
  },
  {
    id: "4",
    name: "New Apartment",
    participantCount: 2,
    imageUrl: "https://pbs.twimg.com/media/G0G5GtbWsAAHIva?format=jpg&name=medium",
  },
  {
    id: "5",
    name: "Weekend Getaway",
    participantCount: 4,
    imageUrl: "https://pbs.twimg.com/media/G0G5GtbWsAAHIva?format=jpg&name=medium",
  },
];

export const mockGetGroupsApi = (): Promise<Group[]> => {
  return new Promise((resolve) => {
    setTimeout(() => {
      resolve(mockGroups);
    }, 100);
  });
};

export const mockTransactionsWithGroupId = [
  {
    id: '1',
    groupId: '1',
    name: 'Groceries',
    payer: 'Alice',
    date: '2025-09-06',
    status: 'completed' as const,
  },
  {
    id: '2',
    groupId: '1',
    name: 'Movie Tickets',
    payer: 'Bob',
    date: '2025-09-05',
    status: 'pending' as const,
  },
  {
    id: '3',
    groupId: '2',
    name: 'Restaurant',
    payer: 'Alice',
    date: '2025-09-04',
    status: 'completed' as const,
  },
];

export const mockGetTransactionsApi = (groupId: string): Promise<any[]> => {
  return new Promise((resolve) => {
    setTimeout(() => {
      resolve(mockTransactionsWithGroupId.filter(t => t.groupId === groupId));
    }, 100);
  });
};

export const mockGetGroupDetailsApi = (groupId: string): Promise<Group | undefined> => {
  return new Promise((resolve) => {
    setTimeout(() => {
      resolve(mockGroups.find(g => g.id === groupId));
    }, 100);
  });
};

const mockUsers: User[] = [
  { id: '1', name: 'Alice', email: 'alice@example.com', imageUrl: 'https://randomuser.me/api/portraits/women/1.jpg', phone: '123-456-7890' },
  { id: '2', name: 'Bob', email: 'bob@example.com', imageUrl: 'https://randomuser.me/api/portraits/men/2.jpg', phone: '234-567-8901' },
  { id: '3', name: 'Charlie', email: 'charlie@example.com', imageUrl: 'https://randomuser.me/api/portraits/men/3.jpg', phone: '345-678-9012' },
  { id: '4', name: 'David', email: 'david@example.com', imageUrl: 'https://randomuser.me/api/portraits/men/4.jpg', phone: '456-789-0123' },
  { id: '5', name: 'Eve', email: 'eve@example.com', imageUrl: 'https://randomuser.me/api/portraits/women/5.jpg', phone: '567-890-1234' },
];

export const mockSearchUsersApi = (username: string): Promise<User[]> => {
  return new Promise((resolve) => {
    setTimeout(() => {
      if (username === '') {
        resolve([]);
      } else {
        const results = mockUsers.filter(user =>
          user.name.toLowerCase().includes(username.toLowerCase())
        );
        resolve(results);
      }
    }, 300);
  });
};

export const mockCreateGroupApi = (groupName: string, participants: User[]): Promise<Group> => {
  return new Promise((resolve) => {
    setTimeout(() => {
      const newGroup: Group = {
        id: (mockGroups.length + 1).toString(),
        name: groupName,
        participantCount: participants.length,
        imageUrl: 'https://via.placeholder.com/150',
      };
      mockGroups.push(newGroup);
      resolve(newGroup);
    }, 1000);
  });
};

export const mockGetPaymentDetailsApi = (transactionId: string): Promise<PaymentDetails> => {
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      if (transactionId === '1') {
        resolve({
          transactionId: '1',
          payerName: 'Alice',
          amountToPay: 25.00,
          qrCodeUrl: 'https://upload.wikimedia.org/wikipedia/commons/d/d0/QR_code_for_mobile_English_Wikipedia.svg',
        });
      } else if (transactionId === '2') {
        resolve({
          transactionId: '2',
          payerName: 'Bob',
          amountToPay: 15.00,
          qrCodeUrl: 'https://upload.wikimedia.org/wikipedia/commons/d/d0/QR_code_for_mobile_English_Wikipedia.svg',
        });
      } else {
        reject(new Error('Payment details not found'));
      }
    }, 500);
  });
};
