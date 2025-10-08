'use client';

import React from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { 
  BarChart, 
  Bar, 
  LineChart, 
  Line, 
  AreaChart, 
  Area, 
  XAxis, 
  YAxis, 
  CartesianGrid, 
  Tooltip, 
  Legend, 
  ResponsiveContainer 
} from 'recharts';
import { 
  ChartContainer, 
  ChartTooltip, 
  ChartTooltipContent,
  ChartLegend,
  ChartLegendContent 
} from '@/components/ui/chart';

const AdminDashboard: React.FC = () => {
  // Mock data for charts
  const usersData = [
    { month: 'Jan', users: 1200 },
    { month: 'Feb', users: 1900 },
    { month: 'Mar', users: 1500 },
    { month: 'Apr', users: 1800 },
    { month: 'May', users: 2100 },
    { month: 'Jun', users: 2300 },
  ];

  const clusterData = [
    { month: 'Jan', active: 45, pending: 12 },
    { month: 'Feb', active: 50, pending: 8 },
    { month: 'Mar', active: 52, pending: 5 },
    { month: 'Apr', active: 55, pending: 7 },
    { month: 'May', active: 56, pending: 3 },
    { month: 'Jun', active: 58, pending: 4 },
  ];

  const resourceData = [
    { cluster: 'Cluster 1', cpu: 65, memory: 72 },
    { cluster: 'Cluster 2', cpu: 42, memory: 58 },
    { cluster: 'Cluster 3', cpu: 81, memory: 76 },
    { cluster: 'Cluster 4', cpu: 35, memory: 45 },
    { cluster: 'Cluster 5', cpu: 78, memory: 82 },
  ];

  const chartConfig = {
    users: {
      label: "Users",
      color: "#2563eb",
    },
    active: {
      label: "Active Clusters",
      color: "#10b981",
    },
    pending: {
      label: "Pending Clusters",
      color: "#f59e0b",
    },
    cpu: {
      label: "CPU Utilization (%)",
      color: "#3b82f6",
    },
    memory: {
      label: "Memory Utilization (%)",
      color: "#ef4444",
    },
  };

  return (
    <div className="p-6">
      <h1 className="text-3xl font-bold mb-6">Admin Dashboard</h1>
      
      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Total Users</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">2,340</div>
            <p className="text-xs text-muted-foreground">+180 from last month</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Active Clusters</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">58</div>
            <p className="text-xs text-muted-foreground">+2 from last month</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Pending Requests</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">4</div>
            <p className="text-xs text-muted-foreground">-8 from last month</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Uptime</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">99.8%</div>
            <p className="text-xs text-muted-foreground">+0.2% from last month</p>
          </CardContent>
        </Card>
      </div>

      {/* Charts Section - 3 columns layout */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-8">
        {/* Users Growth Chart */}
        <Card>
          <CardHeader>
            <CardTitle>Users Growth</CardTitle>
          </CardHeader>
          <CardContent>
            <ChartContainer config={chartConfig} className="min-h-[250px] w-full">
              <AreaChart data={usersData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="month" />
                <YAxis />
                <ChartTooltip content={<ChartTooltipContent />} />
                <Area type="monotone" dataKey="users" fill="var(--color-users)" stroke="var(--color-users)" fillOpacity={0.3} />
              </AreaChart>
            </ChartContainer>
          </CardContent>
        </Card>

        {/* Cluster Status Chart */}
        <Card>
          <CardHeader>
            <CardTitle>Cluster Status</CardTitle>
          </CardHeader>
          <CardContent>
            <ChartContainer config={chartConfig} className="min-h-[250px] w-full">
              <BarChart data={clusterData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="month" />
                <YAxis />
                <ChartTooltip content={<ChartTooltipContent />} />
                <ChartLegend content={<ChartLegendContent />} />
                <Bar dataKey="active" fill="var(--color-active)" name="Active Clusters" />
                <Bar dataKey="pending" fill="var(--color-pending)" name="Pending Clusters" />
              </BarChart>
            </ChartContainer>
          </CardContent>
        </Card>

        {/* Resource Utilization Chart */}
        <Card>
          <CardHeader>
            <CardTitle>Resource Utilization</CardTitle>
          </CardHeader>
          <CardContent>
            <ChartContainer config={chartConfig} className="min-h-[250px] w-full">
              <LineChart data={resourceData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="cluster" />
                <YAxis domain={[0, 100]} />
                <ChartTooltip content={<ChartTooltipContent />} />
                <ChartLegend content={<ChartLegendContent />} />
                <Line type="monotone" dataKey="cpu" stroke="var(--color-cpu)" name="CPU Utilization (%)" strokeWidth={2} dot={false} />
                <Line type="monotone" dataKey="memory" stroke="var(--color-memory)" name="Memory Utilization (%)" strokeWidth={2} dot={false} />
              </LineChart>
            </ChartContainer>
          </CardContent>
        </Card>
      </div>

      {/* Recent Activity */}
      <Card>
        <CardHeader>
          <CardTitle>Recent Activity</CardTitle>
        </CardHeader>
        <CardContent>
          <ul className="space-y-4">
            <li className="flex justify-between items-center border-b pb-2">
              <span>New cluster created: Production-DB-01</span>
              <span className="text-sm text-muted-foreground">2 hours ago</span>
            </li>
            <li className="flex justify-between items-center border-b pb-2">
              <span>User registration: john@example.com</span>
              <span className="text-sm text-muted-foreground">5 hours ago</span>
            </li>
            <li className="flex justify-between items-center border-b pb-2">
              <span>Cluster maintenance: Dev-Cluster-02</span>
              <span className="text-sm text-muted-foreground">1 day ago</span>
            </li>
            <li className="flex justify-between items-center border-b pb-2">
              <span>Security update applied</span>
              <span className="text-sm text-muted-foreground">2 days ago</span>
            </li>
          </ul>
        </CardContent>
      </Card>
    </div>
  );
};

export default AdminDashboard;
