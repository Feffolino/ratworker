// Rat Worker - KubeJS examples (server_scripts). Copy what you need into the pack's kubejs/server_scripts/.

// 1) A scripted copy of the built-in crank task, using the input-simulation helpers.
//    Proves the primitive is scriptable: walk to a hand crank and hold right click on it.
WorkerEvents.registerTasks(event => {
  event.create('crank_js')
    .title('Turn crank (script)')
    .description('Same as the built-in crank, written in KubeJS.')
    .icon('create:hand_crank')
    .cost(1.5, 1.5)
    .range(16)
    .learnXp(30)
    .speedBranch(true)
    .onStart(ctx => {
      let found = null
      let best = 1e9
      let origin = ctx.worker.blockPosition()
      BlockPos.betweenClosed(origin.offset(-8, -3, -8), origin.offset(8, 3, 8)).forEach(p => {
        if (ctx.level.getBlock(p).id == 'create:hand_crank') {
          let d = p.distSqr(origin)
          if (d < best) { best = d; found = p.immutable() }
        }
      })
      if (found == null) { ctx.stopTask('No hand crank nearby'); return }
      ctx.state.putLong('Crank', found.asLong())
    })
    .onTick(ctx => {
      if (!ctx.state.contains('Crank')) return
      let pos = BlockPos.of(ctx.state.getLong('Crank'))
      if (ctx.level.getBlock(pos).id != 'create:hand_crank') { ctx.stopTask('The crank is gone'); return }
      if (ctx.worker.getEyePosition().distanceTo(pos.getCenter()) > 3) {
        ctx.release('use')
        if (ctx.moveTo(pos, 1.8) == 'failed') ctx.stopTask('Cannot reach the crank')
        return
      }
      ctx.holdUse(pos)            // re-sent every performance.holdIntervalTicks while held
      ctx.setStatus('Turning the crank')
    })
})

// 2) A simple job: chop logs within range with an axe (the worker must carry one).
WorkerEvents.registerTasks(event => {
  event.create('chop')
    .title('Chop wood')
    .icon('minecraft:iron_axe')
    .requires('#minecraft:axes')
    .cost(1.2, 1.0)
    .range(8)
    .learnXp(40)
    .onTick(ctx => {
      if (!ctx.cooldown('swing', 30)) return
      let origin = ctx.worker.blockPosition()
      let log = null
      BlockPos.betweenClosed(origin.offset(-8, 0, -8), origin.offset(8, 6, 8)).forEach(p => {
        if (log == null && ctx.level.getBlock(p).hasTag('minecraft:logs')) log = p.immutable()
      })
      if (log == null) { ctx.setStatus('No trees left'); return }
      if (ctx.moveTo(log, 2.5) != 'arrived') return
      ctx.breakBlock(log)
      ctx.pickupItems(4)
    })
})

// 3) Progression hooks: double XP while cranking, refuse tomes before an era, story worker.
WorkerEvents.gainXp(event => {
  if (event.task == 'ratworker:crank') event.amount = event.amount * 2
})

WorkerEvents.learn(event => {
  if (event.task == 'ratworker:farm' && !event.player.stages.has('ratlab_era2')) {
    event.player.tell('The greenhouse is not running yet.')
    event.cancel()
  }
})

// Give Nick when a quest runs: /ratworker ... or from a script:
// let nick = RatWorker.spawn(player.level, player.blockPosition(), player, 'Nick', 'ratworker:textures/entity/worker/nick.png')
// RatWorker.learn(nick, 'crank'); RatWorker.addXp(nick, 50); RatWorker.setNode(nick, 'ratworker:crank#speed', 2)
// player.give(RatWorker.tome('farm'))
