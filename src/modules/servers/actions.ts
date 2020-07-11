import {ActionTree} from 'vuex';
import {ServersState, Server, ServerList} from './types';
import {RootState} from '@/types';
import axios, {AxiosResponse} from 'axios'
import {asyncForEach, logVerbose, queryServer} from '@/utils';


export interface ServerListResponse {
    status: string;
    servers: Server[];
}

export const actions: ActionTree<ServersState, RootState> = {
    fetchServers({rootState, commit, dispatch, state}, projectid?): Promise<void> {
        commit('setLoading', true);
        return axios.post<ServerListResponse>('https://api.creeper.host/minetogether/list', {projectid: projectid}, {
            headers: {
                'Content-Type': 'application/json',
            },
        })
            .then(async (response: AxiosResponse<ServerListResponse>) => {
                let servers = response.data.servers;
                servers.forEach(async (server) => {
                    server.protoResponse = await queryServer(server.ip)
                    commit('updateServer', {id: projectid, server})
                });
                commit('loadServers', {id: projectid, servers});
                commit('setLoading', false);
            }).catch((err) => {
                commit('setLoading', false);
                console.error(err);
            });
    },
    fetchFeaturedServers({rootState, commit, dispatch, state}): Promise<void> {
        commit('setLoading', true);
        return axios.post<ServerListResponse>('https://api.creeper.host/minetogether/list', {
            headers: {
                'Content-Type': 'application/json',
            },
        })
            .then(async (response: AxiosResponse<ServerListResponse>) => {
                //@ts-ignore
                let ftbServers = response.data.servers.filter((f) => isNaN(f.project))
                // const servers = data.servers;
                commit('loadFeaturedServers', ftbServers);
                commit('setLoading', false);
            }).catch((err) => {
                commit('setLoading', false);
                console.error(err);
            });
    },
};
